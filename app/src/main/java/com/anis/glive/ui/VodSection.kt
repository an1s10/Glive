package com.anis.glive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anis.glive.GliveApp
import com.anis.glive.data.Episode
import com.anis.glive.data.Movie
import com.anis.glive.data.MovieInfo
import com.anis.glive.data.Series
import com.anis.glive.data.SeriesInfo
import kotlinx.coroutines.delay

private class Focused(val title: String, val rating: Double, val image: String, val extra: String)

@Composable
private fun VodHeader(f: Focused?, fallback: String) {
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        if (f != null && f.image.isNotBlank()) {
            AsyncImage(f.image, null, contentScale = ContentScale.Crop, alpha = 0.35f,
                modifier = Modifier.align(Alignment.CenterEnd).width(420.dp).height(150.dp))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(C.bg, C.bg, Color.Transparent))))
        }
        Column(Modifier.padding(start = 40.dp, top = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(f?.title ?: fallback, style = T.h1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(560.dp))
            if (f != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (f.rating > 0) RatingBadge(f.rating)
                if (f.extra.isNotBlank()) Text(f.extra, style = T.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(520.dp))
            }
        }
    }
}

@Composable
fun MoviesSection() {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val langs by app.prefs.langs.collectAsState()
    val hidden by app.prefs.hidden.collectAsState()
    val cats = remember(catalog, langs, hidden) { app.repo.visibleVodCats(catalog) }
    var focused by remember { mutableStateOf<Focused?>(null) }
    if (catalog.movies.isEmpty()) { EmptyState("Chargement des films…"); return }
    Column(Modifier.fillMaxSize()) {
        VodHeader(focused, "Films")
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(cats) { cat ->
                val list = catalog.moviesByCat[cat.id].orEmpty()
                Column {
                    Row(Modifier.padding(start = 40.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Flag(cat.lang)
                        Text(cleanName(cat.name), style = T.h2)
                        Text("${list.size}", style = T.small)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(list) { m ->
                            Poster(m.poster, m.name, m.rating, onFocus = { focused = Focused(m.name, m.rating, m.poster, "") }) {
                                nav.push(Route.MovieDetail(m))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesSection() {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val langs by app.prefs.langs.collectAsState()
    val hidden by app.prefs.hidden.collectAsState()
    val cats = remember(catalog, langs, hidden) { app.repo.visibleSeriesCats(catalog) }
    var focused by remember { mutableStateOf<Focused?>(null) }
    if (catalog.series.isEmpty()) { EmptyState("Chargement des séries…"); return }
    Column(Modifier.fillMaxSize()) {
        VodHeader(focused, "Séries")
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(cats) { cat ->
                val list = catalog.seriesByCat[cat.id].orEmpty()
                Column {
                    Row(Modifier.padding(start = 40.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Flag(cat.lang)
                        Text(cleanName(cat.name), style = T.h2)
                        Text("${list.size}", style = T.small)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(list) { s ->
                            Poster(s.poster, s.name, s.rating, onFocus = { focused = Focused(s.name, s.rating, s.poster, listOf(s.year, s.plot).filter { it.isNotBlank() }.joinToString(" · ")) }) {
                                nav.push(Route.SeriesDetail(s))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Backdrop(url: String) {
    Box(Modifier.fillMaxSize()) {
        if (url.isNotBlank()) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.5f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.bg, Color(0xE60A0C10), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.bg))))
    }
}

private val primaryButton: androidx.tv.material3.ButtonColors @Composable get() = ButtonDefaults.colors(
    containerColor = C.text, contentColor = C.bg, focusedContainerColor = C.text, focusedContentColor = C.bg,
)

@Composable
fun MovieDetailScreen(movie: Movie) {
    val app = GliveApp.instance
    val nav = LocalNav.current
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    val resumes by app.prefs.resumes.collectAsState()
    val key = "movie:${movie.id}"
    val resume = resumes.firstOrNull { it.key == key }
    val fr = remember { FocusRequester() }
    LaunchedEffect(movie.id) {
        info = runCatching { app.repo.xtream?.movieInfo(movie.id) }.getOrNull()
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    val rating = info?.rating?.takeIf { it > 0 } ?: movie.rating

    fun play(fromStart: Boolean) {
        if (fromStart) app.prefs.saveResume(key, movie.name, movie.poster, 0, 1)
        nav.push(Route.Vod(
            url = app.repo.xtream!!.movieUrl(movie.id, movie.ext),
            title = movie.name, subtitle = "Film", resumeKey = key,
            image = info?.backdrop?.takeIf { it.isNotBlank() } ?: movie.poster,
        ))
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Backdrop(info?.backdrop?.takeIf { it.isNotBlank() } ?: movie.poster)
        Row(Modifier.padding(start = 48.dp, top = 48.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(Modifier.width(180.dp).height(270.dp).background(C.surface2)) {
                if (movie.poster.isNotBlank()) AsyncImage(movie.poster, movie.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(movie.name, style = T.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rating > 0) RatingBadge(rating)
                    Flag(movie.lang)
                    Text(listOfNotNull(info?.year, info?.duration, info?.genre).filter { it.isNotBlank() }.joinToString(" · "), style = T.body)
                }
                Text(info?.plot ?: "", style = T.body, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (!info?.cast.isNullOrBlank()) Text("Avec : ${info?.cast}", style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (resume != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Bar(resume.position.toFloat() / resume.duration, Modifier.width(180.dp))
                    Text("Reste ${fmtDuration(resume.duration - resume.position)}", style = T.small)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { play(false) }, colors = primaryButton, modifier = Modifier.focusRequester(fr)) {
                        Text(if (resume != null) "▶  Reprendre" else "▶  Lecture")
                    }
                    if (resume != null) Button(onClick = { play(true) }) { Text("Depuis le début") }
                }
            }
        }
    }
}

fun episodeRoute(seriesId: Int, seriesName: String, image: String, episodes: List<Episode>, index: Int): Route.Vod {
    val e = episodes[index]
    val x = GliveApp.instance.repo.xtream!!
    return Route.Vod(
        url = x.episodeUrl(e.id, e.ext),
        title = "É${e.number} · ${e.title}",
        subtitle = seriesName,
        resumeKey = "ep:$seriesId:${e.id}",
        image = e.image.ifBlank { image },
        episodes = episodes,
        episodeIndex = index,
        seriesId = seriesId,
    )
}

@Composable
fun SeriesDetailScreen(series: Series) {
    val app = GliveApp.instance
    val nav = LocalNav.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    val resumes by app.prefs.resumes.collectAsState()
    var season by remember { mutableIntStateOf(-1) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(series.id) {
        info = runCatching { app.repo.xtream?.seriesInfo(series.id) }.getOrNull()
        loading = false
    }
    val seasons = info?.seasons.orEmpty()
    val lastResume = resumes.firstOrNull { it.key.startsWith("ep:${series.id}:") }
    val allEps = remember(seasons) { seasons.values.flatten() }
    val resumeEp = lastResume?.let { r -> allEps.firstOrNull { "ep:${series.id}:${it.id}" == r.key } }
    LaunchedEffect(seasons, resumeEp) {
        if (season == -1 && seasons.isNotEmpty()) season = resumeEp?.season ?: seasons.keys.first()
    }
    LaunchedEffect(loading) { if (!loading) { delay(80); runCatching { fr.requestFocus() } } }
    val eps = seasons[season].orEmpty()
    val rating = info?.rating?.takeIf { it > 0 } ?: series.rating

    fun playEp(list: List<Episode>, i: Int) = nav.push(episodeRoute(series.id, series.name, info?.backdrop ?: series.poster, list, i))

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Backdrop(info?.backdrop?.takeIf { it.isNotBlank() } ?: series.poster)
        LazyColumn(contentPadding = PaddingValues(top = 44.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.padding(start = 48.dp).width(640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SÉRIE · ${seasons.size} SAISON${if (seasons.size > 1) "S" else ""}", style = T.small, color = C.dim)
                    Text(series.name, style = T.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (rating > 0) RatingBadge(rating)
                        Flag(series.lang)
                        Text(listOfNotNull(info?.year ?: series.year, info?.genre).filter { it.isNotBlank() }.joinToString(" · "), style = T.body)
                    }
                    Text(info?.plot?.ifBlank { series.plot } ?: series.plot, style = T.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (loading) Text("Chargement des épisodes…", style = T.small)
                    if (!loading && seasons.isEmpty()) Text("Aucun épisode trouvé.", style = T.small)
                    if (allEps.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val target = resumeEp ?: allEps.first()
                        val list = seasons[target.season].orEmpty()
                        Button(
                            onClick = { playEp(list, list.indexOf(target).coerceAtLeast(0)) },
                            colors = primaryButton, modifier = Modifier.focusRequester(fr),
                        ) { Text(if (resumeEp != null) "▶  Reprendre S${target.season} · É${target.number}" else "▶  Lecture S${target.season} · É${target.number}") }
                    }
                }
            }
            if (seasons.size > 1) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons.keys.toList()) { s -> Pill("Saison $s", s == season) { season = s } }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(eps.size) { i ->
                        val e = eps[i]
                        val r = resumes.firstOrNull { it.key == "ep:${series.id}:${e.id}" }
                        FocusCard(onClick = { playEp(eps, i) }, modifier = Modifier.width(230.dp)) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(130.dp).background(C.surface2), contentAlignment = Alignment.Center) {
                                    Text("É${e.number}", style = T.h1, color = C.dim)
                                    if (e.image.isNotBlank()) AsyncImage(e.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    if (r != null) Bar(r.position.toFloat() / r.duration, Modifier.fillMaxWidth().align(Alignment.BottomCenter), height = 4.dp, track = Color(0x55FFFFFF))
                                }
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("É${e.number} · ${e.title}", style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(e.duration, e.plot).filter { it.isNotBlank() }.joinToString(" · "), style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
