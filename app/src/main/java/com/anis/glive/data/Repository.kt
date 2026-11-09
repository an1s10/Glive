package com.anis.glive.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val epgLock = Mutex()

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

    private suspend fun cached(name: String, url: String, force: Boolean): String {
        val f = File(cacheDir(), "$name.json")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 12 * 3600_000L
        if (!force && fresh) return f.readText()
        return try {
            val body = xtream!!.fetch(url)
            f.writeText(body)
            body
        } catch (e: Exception) {
            if (f.exists()) f.readText() else throw e
        }
    }

    fun load(force: Boolean) {
        val x = xtream ?: return
        scope.launch {
            try {
                if (_catalog.value.channels.isEmpty()) _state.value = LoadState.Loading("Chargement des chaînes…")
                val liveCatsBody = async { cached("live_cats", x.apiUrl("get_live_categories"), force) }
                val vodCatsBody = async { cached("vod_cats", x.apiUrl("get_vod_categories"), force) }
                val serCatsBody = async { cached("series_cats", x.apiUrl("get_series_categories"), force) }
                val liveBody = async { cached("live", x.apiUrl("get_live_streams"), force) }

                val liveCats = x.parseCategories(liveCatsBody.await())
                val liveMap = liveCats.associateBy { it.id }
                val channels = x.parseChannels(liveBody.await(), liveMap)
                _catalog.value = Catalog(liveCats = liveCats, channels = channels)
                _state.value = LoadState.Ready

                val vodBody = async { cached("vod", x.apiUrl("get_vod_streams"), force) }
                val serBody = async { cached("series", x.apiUrl("get_series"), force) }
                val vodCats = runCatching { x.parseCategories(vodCatsBody.await()) }.getOrDefault(emptyList())
                val serCats = runCatching { x.parseCategories(serCatsBody.await()) }.getOrDefault(emptyList())
                val movies = runCatching { x.parseMovies(vodBody.await(), vodCats.associateBy { it.id }) }.getOrDefault(emptyList())
                val series = runCatching { x.parseSeries(serBody.await(), serCats.associateBy { it.id }) }.getOrDefault(emptyList())
                _catalog.value = Catalog(liveCats, channels, vodCats, movies, serCats, series)
            } catch (e: Exception) {
                if (_catalog.value.channels.isEmpty()) {
                    _state.value = LoadState.Error(e.message ?: "Erreur de chargement")
                }
            }
        }
    }

    /** Programme de la journée (passé et à venir), mis en cache 15 minutes. */
    suspend fun programs(channelId: Int): List<Program> {
        val now = System.currentTimeMillis()
        epgCache[channelId]?.let { (t, l) -> if (now - t < 15 * 60_000L) return l }
        val x = xtream ?: return emptyList()
        return withContext(Dispatchers.IO) {
            epgLock.withLock {
                epgCache[channelId]?.let { (t, l) -> if (now - t < 15 * 60_000L) return@withContext l }
            }
            val list = runCatching { x.programs(channelId) }.getOrDefault(emptyList())
            epgCache[channelId] = now to list
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
