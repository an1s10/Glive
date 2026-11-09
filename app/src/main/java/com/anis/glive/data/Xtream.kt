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
import java.io.IOException
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

class Xtream(val account: Account, userAgent: String) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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

    // ---- URLs de lecture ----
    fun liveUrl(id: Int) = "${account.base}/live/${account.user}/${account.pass}/$id.ts"
    fun movieUrl(id: Int, ext: String) = "${account.base}/movie/${account.user}/${account.pass}/$id.${ext.ifEmpty { "mp4" }}"
    fun episodeUrl(id: String, ext: String) = "${account.base}/series/${account.user}/${account.pass}/$id.${ext.ifEmpty { "mp4" }}"

    /** startRaw au format "yyyy-MM-dd HH:mm:ss", heure du serveur. */
    fun catchupUrl(id: Int, startRaw: String, durationMin: Int): String {
        val s = startRaw.trim()
        val date = s.substring(0, 10)
        val hm = s.substring(11, 16).replace(':', '-')
        return "${account.base}/timeshift/${account.user}/${account.pass}/$durationMin/$date:$hm/$id.ts"
    }

    // ---- Parsing ----
    fun parseCategories(body: String): List<Category> =
        json.parseToJsonElement(body).arr().orEmpty().mapNotNull { it.obj() }.map {
            val name = it.str("category_name")
            Category(it.str("category_id"), name, Lang.detect(name))
        }

    fun parseChannels(body: String, cats: Map<String, Category>): List<Channel> =
        json.parseToJsonElement(body).arr().orEmpty().mapNotNull { it.obj() }.map {
            val cat = it.str("category_id")
            val name = it.str("name")
            val lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name)
            Channel(
                id = it.int("stream_id"),
                num = it.int("num"),
                name = name.trim(),
                logo = it.str("stream_icon"),
                categoryId = cat,
                epgId = it.str("epg_channel_id"),
                archive = it.int("tv_archive") == 1,
                archiveDays = it.int("tv_archive_duration"),
                lang = lang,
            )
        }

    fun parseMovies(body: String, cats: Map<String, Category>): List<Movie> =
        json.parseToJsonElement(body).arr().orEmpty().mapNotNull { it.obj() }.map {
            val cat = it.str("category_id")
            val name = it.str("name")
            Movie(
                id = it.int("stream_id"),
                name = name.trim(),
                poster = it.str("stream_icon"),
                categoryId = cat,
                rating = it.dbl("rating").takeIf { r -> r > 0 } ?: (it.dbl("rating_5based") * 2),
                added = it.long("added"),
                ext = it.str("container_extension"),
                lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name),
            )
        }

    fun parseSeries(body: String, cats: Map<String, Category>): List<Series> =
        json.parseToJsonElement(body).arr().orEmpty().mapNotNull { it.obj() }.map {
            val cat = it.str("category_id")
            val name = it.str("name")
            Series(
                id = it.int("series_id"),
                name = name.trim(),
                poster = it.str("cover"),
                categoryId = cat,
                rating = it.dbl("rating").takeIf { r -> r > 0 } ?: (it.dbl("rating_5based") * 2),
                added = it.long("last_modified"),
                plot = it.str("plot"),
                year = it.str("releaseDate").take(4).ifEmpty { it.str("year") },
                lang = cats[cat]?.lang?.takeIf { l -> l != Lang.OTHER } ?: Lang.detect(name),
            )
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
        }.filter { it.end > it.start }.sortedBy { it.start }
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
