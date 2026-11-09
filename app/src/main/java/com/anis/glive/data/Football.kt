package com.anis.glive.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class League(val code: String, val name: String, val keywords: List<String>)

data class Match(
    val id: String,
    val league: League,
    val home: String,
    val away: String,
    val homeLogo: String,
    val awayLogo: String,
    val homeScore: String,
    val awayScore: String,
    val state: String, // pre, in, post
    val detail: String,
    val time: Long, // epoch ms
    val broadcasts: List<String>,
) {
    val live get() = state == "in"
    val finished get() = state == "post"
    fun involves(team: String) = home.contains(team, true) || away.contains(team, true)
}

object Football {
    val leagues = listOf(
        League("uefa.champions", "Ligue des champions", listOf("bein", "canal", "rmc", "tnt", "tsn", "rds", "cbs", "paramount", "sky", "dazn", "movistar", "amazon")),
        League("esp.1", "Liga", listOf("bein", "dazn", "movistar", "laliga", "tsn", "espn", "premier sports")),
        League("eng.1", "Premier League", listOf("canal", "sky", "tnt", "nbc", "usa network", "fubo", "bein", "dazn")),
        League("fra.1", "Ligue 1", listOf("ligue 1", "dazn", "bein", "beinsport", "tva", "fubo")),
        League("ita.1", "Serie A", listOf("dazn", "bein", "sky", "paramount", "tsn")),
        League("ger.1", "Bundesliga", listOf("bein", "sky", "dazn", "espn", "sportsnet")),
        League("uefa.europa", "Ligue Europa", listOf("canal", "bein", "rmc", "tnt", "tsn", "cbs", "paramount", "dazn")),
        League("usa.1", "MLS", listOf("apple", "tsn", "rds", "fox", "mls")),
        League("caf.nations", "Coupe d'Afrique", listOf("bein", "canal", "eptv", "algerie", "a3", "ent", "tsn")),
    )

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private var cache: Pair<Long, List<Match>>? = null

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val day = SimpleDateFormat("yyyyMMdd", Locale.US)

    suspend fun matches(force: Boolean = false): List<Match> {
        val now = System.currentTimeMillis()
        cache?.let { (t, l) ->
            val ttl = if (l.any { it.live }) 60_000L else 10 * 60_000L
            if (!force && now - t < ttl) return l
        }
        val list = withContext(Dispatchers.IO) {
            coroutineScope {
                val boards = leagues.map { lg -> async { runCatching { fetchLeague(lg) }.getOrDefault(emptyList()) } }
                val team = async { runCatching { teamSchedule(86, "esp.1") }.getOrDefault(emptyList()) }
                boards.awaitAll().flatten() + team.await()
            }
        }.distinctBy { it.id }.sortedWith(compareBy<Match>({ if (it.live) 0 else if (it.finished) 2 else 1 }, { it.time }))
        if (list.isNotEmpty()) cache = now to list
        return list.ifEmpty { cache?.second ?: emptyList() }
    }

    private fun get(url: String): JsonObject? {
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
        return json.parseToJsonElement(body).obj()
    }

    private fun fetchLeague(lg: League): List<Match> {
        val root = get("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.code}/scoreboard") ?: return emptyList()
        return root["events"].arr().orEmpty().mapNotNull { it.obj() }.mapNotNull { ev -> parseEvent(ev, lg) }
    }

    /** Prochains matchs d'une équipe, toutes compétitions. */
    private fun teamSchedule(teamId: Int, leagueCode: String): List<Match> {
        val root = get("https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueCode/teams/$teamId/schedule?fixture=true") ?: return emptyList()
        return root["events"].arr().orEmpty().mapNotNull { it.obj() }.take(6).mapNotNull { ev ->
            val slug = ev["league"].obj()?.str("slug") ?: leagueCode
            val lg = leagues.firstOrNull { it.code == slug }
                ?: League(slug, ev["league"].obj()?.str("shortName")?.ifEmpty { null } ?: "Football", listOf("bein", "dazn", "canal", "tsn", "espn"))
            parseEvent(ev, lg)
        }
    }

    private fun scoreOf(e: JsonElement?): String = when (e) {
        is JsonPrimitive -> e.contentOrNull ?: ""
        is JsonObject -> e.str("displayValue").ifEmpty { e.str("value") }
        else -> ""
    }

    private fun parseEvent(ev: JsonObject, lg: League): Match? {
        val comp = ev["competitions"].arr()?.firstOrNull()?.obj() ?: return null
        val teams = comp["competitors"].arr().orEmpty().mapNotNull { it.obj() }
        val home = teams.firstOrNull { it.str("homeAway") == "home" } ?: teams.getOrNull(0) ?: return null
        val away = teams.firstOrNull { it.str("homeAway") == "away" } ?: teams.getOrNull(1) ?: return null
        val status = comp["status"].obj() ?: ev["status"].obj()
        val type = status?.get("type").obj()
        val state = type?.str("state") ?: "pre"
        val detail = if (state == "in") status?.str("displayClock")?.ifEmpty { null } ?: type?.str("shortDetail") ?: "" else type?.str("shortDetail") ?: ""
        val time = runCatching { iso.parse(ev.str("date").take(16))!!.time }.getOrDefault(0L)
        val broadcasts = comp["broadcasts"].arr().orEmpty().mapNotNull { it.obj() }
            .flatMap { b -> b["names"].arr().orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } + listOfNotNull(b["media"].obj()?.str("shortName")?.ifEmpty { null }) } +
            comp["geoBroadcasts"].arr().orEmpty().mapNotNull { it.obj()?.get("media").obj()?.str("shortName") }
        fun team(o: JsonObject) = o["team"].obj()
        return Match(
            id = ev.str("id"),
            league = lg,
            home = team(home)?.str("shortDisplayName")?.ifEmpty { null } ?: team(home)?.str("displayName") ?: "",
            away = team(away)?.str("shortDisplayName")?.ifEmpty { null } ?: team(away)?.str("displayName") ?: "",
            homeLogo = logoOf(team(home)),
            awayLogo = logoOf(team(away)),
            homeScore = scoreOf(home["score"]),
            awayScore = scoreOf(away["score"]),
            state = state,
            detail = detail,
            time = time,
            broadcasts = broadcasts.distinct(),
        )
    }

    private fun logoOf(t: JsonObject?): String {
        if (t == null) return ""
        t.str("logo").takeIf { it.isNotEmpty() }?.let { return it }
        return t["logos"].arr()?.firstOrNull()?.obj()?.str("href") ?: ""
    }

    /** Chaînes de la playlist qui diffusent probablement ce match, classées par langue préférée. */
    fun channelsFor(m: Match, catalog: Catalog, langs: Set<Lang>): List<Channel> {
        val words = (m.league.keywords + m.broadcasts.map { normalize(it) }).map { normalize(it) }.filter { it.length >= 3 }.distinct()
        val pref = listOf(Lang.FR, Lang.AR, Lang.EN, Lang.CA, Lang.ES)
        return catalog.channels.asSequence()
            .filter { langs.isEmpty() || it.lang in langs }
            .filter { c -> val n = normalize(c.name); words.any { n.contains(it) } }
            .sortedWith(compareBy<Channel>({ pref.indexOf(it.lang).let { i -> if (i < 0) 99 else i } }, { it.num }))
            .take(12)
            .toList()
    }
}
