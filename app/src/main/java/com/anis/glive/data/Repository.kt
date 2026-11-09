package com.anis.glive.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

sealed class LoadState {
    data object Idle : LoadState()
    data class Loading(val step: String) : LoadState()
    data object Ready : LoadState()
    data class Error(val message: String) : LoadState()
}

class Catalog(
    val liveCats: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val vodCats: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val channelById: Map<Int, Channel> = channels.associateBy { it.id }
    val channelsByCat: Map<String, List<Channel>> = channels.groupBy { it.categoryId }
    val moviesByCat: Map<String, List<Movie>> = movies.groupBy { it.categoryId }
    val seriesByCat: Map<String, List<Series>> = series.groupBy { it.categoryId }
    val movieById: Map<Int, Movie> = movies.associateBy { it.id }
    val seriesById: Map<Int, Series> = series.associateBy { it.id }
    val recentMovies: List<Movie> by lazy { movies.sortedByDescending { it.added }.take(40) }
    val recentSeries: List<Series> by lazy { series.sortedByDescending { it.added }.take(40) }
}

fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

class Repository(private val context: Context, val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog

    var xtream: Xtream? = null
        private set

    private val epgCache = ConcurrentHashMap<Int, Pair<Long, List<Program>>>()

    fun start() {
        val acc = prefs.account ?: return
        xtream = Xtream(acc, prefs.userAgent)
        load(force = false)
    }

    fun logout() {
        prefs.account = null
        xtream = null
        _catalog.value = Catalog()
        _state.value = LoadState.Idle
        cacheDir().deleteRecursively()
    }

    private fun cacheDir() = File(context.filesDir, "catalog").apply { mkdirs() }

    private suspend fun cached(name: String, url: String, force: Boolean): File {
        val f = File(cacheDir(), "$name.json")
        val fresh = f.exists() && f.length() > 2 && System.currentTimeMillis() - f.lastModified() < 12 * 3600_000L
        if (!force && fresh) return f
        try {
            xtream!!.download(url, f)
        } catch (e: Exception) {
            if (!f.exists()) throw e
        }
        return f
    }

    fun load(force: Boolean) {
        val x = xtream ?: return
        scope.launch {
            try {
                if (_catalog.value.channels.isEmpty()) _state.value = LoadState.Loading("Chargement des chaînes…")
                val liveCats = x.parseCategories(cached("live_cats", x.apiUrl("get_live_categories"), force))
                val channels = x.parseChannels(cached("live", x.apiUrl("get_live_streams"), force), liveCats.associateBy { it.id })
                if (channels.isEmpty()) throw IllegalStateException("Aucune chaîne reçue du serveur")
                _catalog.value = Catalog(liveCats = liveCats, channels = channels)
                _state.value = LoadState.Ready

                val vodCats = runCatching { x.parseCategories(cached("vod_cats", x.apiUrl("get_vod_categories"), force)) }.getOrDefault(emptyList())
                val movies = runCatching { x.parseMovies(cached("vod", x.apiUrl("get_vod_streams"), force), vodCats.associateBy { it.id }) }.getOrDefault(emptyList())
                _catalog.value = Catalog(liveCats, channels, vodCats, movies)
                val serCats = runCatching { x.parseCategories(cached("series_cats", x.apiUrl("get_series_categories"), force)) }.getOrDefault(emptyList())
                val series = runCatching { x.parseSeries(cached("series", x.apiUrl("get_series"), force), serCats.associateBy { it.id }) }.getOrDefault(emptyList())
                _catalog.value = Catalog(liveCats, channels, vodCats, movies, serCats, series)
            } catch (e: Throwable) {
                if (_catalog.value.channels.isEmpty()) {
                    _state.value = LoadState.Error(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private val epgGate = Semaphore(3)
    private val shortCache = ConcurrentHashMap<Int, Pair<Long, List<Program>>>()

    /** Programme en cours et à venir, léger, pour les listes. Cache 10 min. */
    suspend fun nowNext(channelId: Int): List<Program> {
        val now = System.currentTimeMillis()
        shortCache[channelId]?.let { (t, l) -> if (now - t < 10 * 60_000L && (l.isEmpty() || l.last().end * 1000 > now)) return l }
        epgCache[channelId]?.let { (t, l) -> if (now - t < 15 * 60_000L) return l }
        val x = xtream ?: return emptyList()
        return withContext(Dispatchers.IO) {
            epgGate.withPermit {
                shortCache[channelId]?.let { (t, l) -> if (now - t < 10 * 60_000L) return@withPermit l }
                val list = runCatching { x.shortEpg(channelId) }.getOrDefault(emptyList())
                shortCache[channelId] = System.currentTimeMillis() to list
                list
            }
        }
    }

    fun cachedNowNext(channelId: Int): List<Program>? = shortCache[channelId]?.second ?: epgCache[channelId]?.second

    /** Programme complet (passé et à venir), mis en cache 15 minutes. */
    suspend fun programs(channelId: Int): List<Program> {
        val now = System.currentTimeMillis()
        epgCache[channelId]?.let { (t, l) -> if (now - t < 15 * 60_000L) return l }
        val x = xtream ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val list = runCatching { x.programs(channelId) }.getOrDefault(emptyList())
            epgCache[channelId] = System.currentTimeMillis() to list
            list
        }
    }

    fun cachedPrograms(channelId: Int): List<Program>? = epgCache[channelId]?.second

    fun visibleLiveCats(c: Catalog): List<Category> = filterCats(c.liveCats, "live") { c.channelsByCat[it]?.isNotEmpty() == true }
    fun visibleVodCats(c: Catalog): List<Category> = filterCats(c.vodCats, "vod") { c.moviesByCat[it]?.isNotEmpty() == true }
    fun visibleSeriesCats(c: Catalog): List<Category> = filterCats(c.seriesCats, "series") { c.seriesByCat[it]?.isNotEmpty() == true }

    private fun filterCats(cats: List<Category>, kind: String, nonEmpty: (String) -> Boolean): List<Category> {
        val langs = prefs.langs.value
        val hidden = prefs.hidden.value
        return cats.filter { (langs.isEmpty() || it.lang in langs) && "$kind:${it.id}" !in hidden && nonEmpty(it.id) }
    }
}
