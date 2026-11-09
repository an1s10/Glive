package com.anis.glive.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonObject.str(key: String): String {
    val e = this[key] ?: return ""
    if (e is JsonNull) return ""
    return (e as? JsonPrimitive)?.contentOrNull ?: ""
}

fun JsonObject.int(key: String): Int = str(key).toDoubleOrNull()?.toInt() ?: 0
fun JsonObject.long(key: String): Long = str(key).toDoubleOrNull()?.toLong() ?: 0L
fun JsonObject.dbl(key: String): Double = str(key).replace(',', '.').toDoubleOrNull() ?: 0.0

fun b64(s: String): String {
    if (s.isEmpty()) return ""
    return try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        s
    }
}

class Xtream(val account: Account, val userAgent: String) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun apiUrl(action: String?, extra: String = ""): String {
        val a = account
        var u = "${a.base}/player_api.php?username=${enc(a.user)}&password=${enc(a.pass)}"
        if (action != null) u += "&action=$action"
        return u + extra
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    suspend fun authenticate(): String? {
        return try {
            val body = fetch(apiUrl(null))
            val root = json.parseToJsonElement(body).obj() ?: return "Réponse inattendue du serveur"
            val info = root["user_info"].obj() ?: return "Identifiants refusés"
            if (info.int("auth") != 1) return "Identifiants refusés"
            val status = info.str("status")
            if (status.isNotEmpty() && !status.equals("Active", true)) return "Compte $status"
            null
        } catch (e: Exception) {
            "Connexion impossible : ${e.message ?: "erreur réseau"}"
        }
    }

    /** Teste un flux : code HTTP, type, et si les premiers octets ressemblent à de la vidéo. */
    suspend fun probe(url: String, ua: String): String = withContext(Dispatchers.IO) {
        try {
            val client = http.newBuilder().readTimeout(12, TimeUnit.SECONDS).addInterceptor { c ->
                c.proceed(c.request().newBuilder().header("User-Agent", ua).build())
            }.build()
            client.newCall(Request.Builder().url(url).header("Range", "bytes=0-4095").build()).execute().use { r ->
                val bytes = r.body?.byteStream()?.let { input ->
                    val buf = ByteArray(4096); var n = 0
                    while (n < buf.size) { val k = input.read(buf, n, buf.size - n); if (k <= 0) break; n += k }
                    buf.copyOf(n)
                } ?: ByteArray(0)
                val kind = when {
                    bytes.isNotEmpty() && bytes[0] == 0x47.toByte() -> "flux TS"
                    String(bytes.take(16).toByteArray()).startsWith("#EXTM3U") -> "playlist HLS"
                    bytes.size > 8 && String(bytes.copyOfRange(4, 8)) == "ftyp" -> "MP4"
                    bytes.isEmpty() -> "vide"
                    else -> "inconnu"
                }
                val host = r.request.url.host
                "HTTP ${r.code} · $kind · ${bytes.size} o · ${r.header("Content-Type") ?: "?"} · $host"
            }
        } catch (e: Exception) {
            "ÉCHEC : ${e.javaClass.simpleName} ${e.message ?: ""}".take(120)
        }
    }

    // ---- URLs de lecture ----
    fun liveUrl(id: Int) = "${account.base}/live/${account.user}/${account.pass}/$id.ts"
    fun liveUrlHls(id: Int) = "${account.base}/live/${account.user}/${account.pass}/$id.m3u8"
    fun liveUrlBare(id: Int) = "${account.base}/${account.user}/${account.pass}/$id"
    fun movieUrl(id: Int, ext: String) = "${account.base}/movie/${account.user}/${account.pass}/$id.${ext.ifEmpty { "mp4" }}"
    fun episodeUrl(id: String, ext: String) = "${account.base}/series/${account.user}/${account.pass}/$id.${ext.ifEmpty { "mp4" }}"

    /** startRaw au format "yyyy-MM-dd HH:mm:ss", heure du serveur. */
    fun catchupUrl(id: Int, startRaw: String, durationMin: Int): String {
        val s = startRaw.trim()
        val date = s.substring(0, 10)
        val hm = s.substring(11, 16).replace(':', '-')
        return "${account.base}/timeshift/${account.user}/${account.pass}/$durationMin/$date:$hm/$id.ts"
    }

    /** Télécharge directement dans un fichier, sans tout charger en mémoire. */
    suspend fun download(url: String, dest: File) = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
        }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
    }

    /** Lit un tableau JSON d'objets en flux, en ne gardant que les champs demandés. */
    private fun readObjects(file: File, keys: Set<String>, each: (Map<String, String>) -> Unit) {
        if (!file.exists()) return
        JsonReader(InputStreamReader(file.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) return
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                val m = HashMap<String, String>(keys.size * 2)
                r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    if (name in keys) {
                        when (r.peek()) {
                            JsonToken.STRING, JsonToken.NUMBER -> m[name] = r.nextString()
                            JsonToken.BOOLEAN -> m[name] = r.nextBoolean().toString()
                            JsonToken.NULL -> { r.nextNull() }
                            else -> r.skipValue()
                        }
                    } else r.skipValue()
                }
                r.endObject()
                runCatching { each(m) }
            }
            r.endArray()
        }
    }

    private fun Map<String, String>.s(k: String) = this[k] ?: ""
    private fun Map<String, String>.i(k: String) = this[k]?.toDoubleOrNull()?.toInt() ?: 0
    private fun Map<String, String>.l(k: String) = this[k]?.toDoubleOrNull()?.toLong() ?: 0L
    private fun Map<String, String>.d(k: String) = this[k]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

    fun parseCategories(file: File): List<Category> {
        val out = ArrayList<Category>()
        val seen = HashSet<String>()
        readObjects(file, setOf("category_id", "category_name")) {
            val id = it.s("category_id")
            if (seen.add(id)) out.add(Category(id, it.s("category_name"), Lang.detect(it.s("category_name"))))
        }
        return out
    }

    fun parseChannels(file: File, cats: Map<String, Category>): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<Int>()
        readObjects(file, setOf("stream_id", "num", "name", "stream_icon", "category_id", "epg_channel_id", "tv_archive", "tv_archive_duration")) {
            val id = it.i("stream_id")
            if (id == 0 || !seen.add(id)) return@readObjects
            val cat = it.s("category_id")
            val name = it.s("name").trim()
            out.add(Channel(
                id = id, num = it.i("num"), name = name, logo = it.s("stream_icon"), categoryId = cat,
                epgId = it.s("epg_channel_id"), archive = it.i("tv_archive") == 1, archiveDays = it.i("tv_archive_duration"),
                lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name),
            ))
        }
        return out
    }

    fun parseMovies(file: File, cats: Map<String, Category>): List<Movie> {
        val out = ArrayList<Movie>()
        val seen = HashSet<Int>()
        readObjects(file, setOf("stream_id", "name", "stream_icon", "category_id", "rating", "rating_5based", "added", "container_extension")) {
            val id = it.i("stream_id")
            if (id == 0 || !seen.add(id)) return@readObjects
            val cat = it.s("category_id")
            val name = it.s("name").trim()
            out.add(Movie(
                id = id, name = name, poster = it.s("stream_icon"), categoryId = cat,
                rating = it.d("rating").takeIf { r -> r > 0 } ?: (it.d("rating_5based") * 2),
                added = it.l("added"), ext = it.s("container_extension"),
                lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name),
            ))
        }
        return out
    }

    fun parseSeries(file: File, cats: Map<String, Category>): List<Series> {
        val out = ArrayList<Series>()
        val seen = HashSet<Int>()
        readObjects(file, setOf("series_id", "name", "cover", "category_id", "rating", "rating_5based", "last_modified", "plot", "releaseDate", "year")) {
            val id = it.i("series_id")
            if (id == 0 || !seen.add(id)) return@readObjects
            val cat = it.s("category_id")
            val name = it.s("name").trim()
            out.add(Series(
                id = id, name = name, poster = it.s("cover"), categoryId = cat,
                rating = it.d("rating").takeIf { r -> r > 0 } ?: (it.d("rating_5based") * 2),
                added = it.l("last_modified"), plot = it.s("plot"),
                year = it.s("releaseDate").take(4).ifEmpty { it.s("year") },
                lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name),
            ))
        }
        return out
    }

    /** Programme en cours et suivants (léger), pour les listes. */
    suspend fun shortEpg(streamId: Int): List<Program> {
        val body = fetch(apiUrl("get_short_epg", "&stream_id=$streamId&limit=4"))
        val root = json.parseToJsonElement(body).obj() ?: return emptyList()
        return root["epg_listings"].arr().orEmpty().mapNotNull { it.obj() }.map {
            Program(
                title = b64(it.str("title")),
                description = b64(it.str("description")),
                start = it.long("start_timestamp"),
                end = it.long("stop_timestamp"),
                hasArchive = it.int("has_archive") == 1,
                startRaw = it.str("start"),
            )
        }.filter { it.end > it.start }.sortedBy { it.start }.distinctBy { it.start }
    }

    suspend fun programs(streamId: Int): List<Program> {
        val body = fetch(apiUrl("get_simple_data_table", "&stream_id=$streamId"))
        val root = json.parseToJsonElement(body).obj() ?: return emptyList()
        return root["epg_listings"].arr().orEmpty().mapNotNull { it.obj() }.map {
            Program(
                title = b64(it.str("title")),
                description = b64(it.str("description")),
                start = it.long("start_timestamp"),
                end = it.long("stop_timestamp"),
                hasArchive = it.int("has_archive") == 1,
                startRaw = it.str("start"),
            )
        }.filter { it.end > it.start }.sortedBy { it.start }.distinctBy { it.start }
    }

    suspend fun movieInfo(id: Int): MovieInfo? {
        val body = fetch(apiUrl("get_vod_info", "&vod_id=$id"))
        val info = json.parseToJsonElement(body).obj()?.get("info").obj() ?: return null
        val backdrop = info["backdrop_path"].arr()?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: info.str("backdrop_path")
        return MovieInfo(
            plot = info.str("plot").ifEmpty { info.str("description") },
            genre = info.str("genre"),
            duration = info.str("duration"),
            rating = info.dbl("rating"),
            backdrop = backdrop ?: "",
            year = info.str("releasedate").take(4).ifEmpty { info.str("release_date").take(4) },
            cast = info.str("cast"),
        )
    }

    suspend fun seriesInfo(id: Int): SeriesInfo? {
        val body = fetch(apiUrl("get_series_info", "&series_id=$id"))
        val root = json.parseToJsonElement(body).obj() ?: return null
        val info = root["info"].obj()
        val seasons = mutableMapOf<Int, List<Episode>>()
        val eps = root["episodes"]
        val groups: List<Pair<String, JsonElement>> = when (eps) {
            is JsonObject -> eps.entries.map { it.key to it.value }
            is JsonArray -> eps.mapIndexed { i, e -> (i + 1).toString() to e }
            else -> emptyList()
        }
        for ((key, list) in groups) {
            val season = key.toIntOrNull() ?: continue
            seasons[season] = list.arr().orEmpty().mapNotNull { it.obj() }.map { e ->
                val ei = e["info"].obj()
                Episode(
                    id = e.str("id"),
                    title = e.str("title"),
                    season = season,
                    number = e.int("episode_num"),
                    ext = e.str("container_extension"),
                    plot = ei?.str("plot") ?: "",
                    duration = ei?.str("duration") ?: "",
                    image = ei?.str("movie_image") ?: "",
                )
            }.sortedBy { it.number }
        }
        val backdrop = info?.get("backdrop_path").arr()?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
        return SeriesInfo(
            plot = info?.str("plot") ?: "",
            genre = info?.str("genre") ?: "",
            rating = info?.dbl("rating") ?: 0.0,
            backdrop = backdrop ?: info?.str("cover") ?: "",
            year = info?.str("releaseDate")?.take(4) ?: "",
            seasons = seasons.toSortedMap(),
        )
    }
}
