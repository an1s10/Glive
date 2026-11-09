package com.anis.glive.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anis.glive.GliveApp
import com.anis.glive.data.Channel
import com.anis.glive.data.Program
import kotlinx.coroutines.delay

enum class Section(val label: String, val icon: String) {
    HOME("Accueil", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"),
    LIVE("Direct", "M4 7h16v12H4zM9 3l3 4 3-4"),
    GUIDE("Guide TV", "M3 5h18M3 12h18M3 19h18M8 5v14"),
    FOOT("Matchs", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7l4 3-1.5 4.5h-5L8 10z"),
    MOVIES("Films", "M4 4h16v16H4zM4 9h16M4 15h16M9 4v16M15 4v16"),
    SERIES("Séries", "M3 6h18v12H3zM8 21h8M12 18v3"),
    SEARCH("Recherche", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4"),
    SETTINGS("Réglages", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"),
}

@Composable
fun NavIcon(path: String, color: Color, modifier: Modifier = Modifier) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(22.dp)) {
        val s = size.width / 24f
        drawContext.transform.scale(s, s, androidx.compose.ui.geometry.Offset.Zero)
        drawPath(p, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    val nav = LocalNav.current
    var section by nav::section
    var pending by remember { mutableStateOf<Section?>(null) }
    LaunchedEffect(pending) { pending?.let { delay(320); section = it } }
    var railFocused by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(if (railFocused) 190.dp else 64.dp, label = "rail")

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBack by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler {
        if (section != Section.HOME) { section = Section.HOME; pending = null; return@BackHandler }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2500) (context as? android.app.Activity)?.finish()
        else {
            lastBack = now
            android.widget.Toast.makeText(context, "Appuie encore sur Retour pour quitter Glive", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Row(Modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(if (railFocused) C.surface else C.bg)
                .onFocusChanged { railFocused = it.hasFocus }
                .padding(top = 28.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("G", style = T.h1, color = C.accent, modifier = Modifier.padding(start = 12.dp, bottom = 22.dp))
            Section.entries.forEach { s ->
                val selected = s == section
                FocusCard(
                    onClick = { section = s },
                    modifier = Modifier.fillMaxWidth().height(42.dp).onFocusGained { pending = s },
                    color = if (selected) C.surface2 else Color.Transparent,
                    focusedColor = C.accent,
                    focusedScale = 1f,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        NavIcon(s.icon, if (selected || railFocused) C.text else C.dim)
                        if (railFocused) Text(s.label, style = T.label, maxLines = 1)
                    }
                }
            }
        }
        androidx.compose.animation.Crossfade(
            targetState = section,
            animationSpec = androidx.compose.animation.core.tween(260),
            modifier = Modifier.fillMaxSize(),
            label = "section",
        ) { current ->
            when (current) {
                Section.HOME -> HomeContent()
                Section.LIVE -> LiveSection()
                Section.GUIDE -> GuideSection()
                Section.FOOT -> FootballSection()
                Section.MOVIES -> MoviesSection()
                Section.SERIES -> SeriesSection()
                Section.SEARCH -> SearchSection()
                Section.SETTINGS -> SettingsSection(onLogout)
            }
        }
    }
}

/** Programme complet d'une chaîne (guide). */
@Composable
fun rememberPrograms(channelId: Int): List<Program> {
    val repo = GliveApp.instance.repo
    var list by remember(channelId) { mutableStateOf(repo.cachedPrograms(channelId) ?: emptyList()) }
    LaunchedEffect(channelId) {
        while (true) {
            list = repo.programs(channelId)
            delay(5 * 60_000)
        }
    }
    return list
}

/** Programme en cours et suivant, léger : une seule requête, relancée à la fin de l'émission. */
@Composable
fun rememberNowNext(channelId: Int): List<Program> {
    val repo = GliveApp.instance.repo
    var list by remember(channelId) { mutableStateOf(repo.cachedNowNext(channelId) ?: emptyList()) }
    LaunchedEffect(channelId) {
        delay(150) // ne charge pas les chaînes qu'on fait juste défiler
        while (true) {
            list = repo.nowNext(channelId)
            val end = list.firstOrNull { it.isNow(System.currentTimeMillis() / 1000) }?.end
            val wait = if (end != null) (end * 1000 - System.currentTimeMillis()).coerceIn(30_000, 30 * 60_000) else 10 * 60_000L
            delay(wait)
        }
    }
    return list
}

@Composable
fun nowSec(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() / 1000 }
    }
    return now
}

@Composable
fun HomeContent() {
    val app = GliveApp.instance
    val repo = app.repo
    val nav = LocalNav.current
    val catalog by repo.catalog.collectAsState()
    val favIds by app.prefs.favorites.collectAsState()
    val recentIds by app.prefs.recents.collectAsState()
    val resumes by app.prefs.resumes.collectAsState()

    val favs = remember(catalog, favIds) { favIds.mapNotNull { catalog.channelById[it] }.sortedBy { it.num } }
    val recents = remember(catalog, recentIds) { recentIds.mapNotNull { catalog.channelById[it] } }
    var hero by remember { mutableStateOf<Channel?>(null) }
    val heroCh = hero ?: recents.firstOrNull() ?: favs.firstOrNull() ?: catalog.channels.firstOrNull()

    val matches = rememberMatches()
    val team = app.prefs.favoriteTeam
    val heroMatch = remember(matches) {
        matches.firstOrNull { it.live && it.involves(team) }
            ?: matches.firstOrNull { !it.finished && it.involves(team) && it.time - System.currentTimeMillis() < 14 * 86_400_000L }
            ?: matches.firstOrNull { it.live && it.league.code == "uefa.champions" }
    }
    val footRow = remember(matches) {
        (matches.filter { it.live } + matches.filter { !it.finished && !it.live && (it.involves(team) || it.league.code == "uefa.champions") })
            .distinctBy { it.id }.take(15)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            if (heroMatch != null) MatchHero(heroMatch)
            else if (heroCh != null) HeroChannel(heroCh, favs.ifEmpty { recents }.ifEmpty { listOf(heroCh) })
        }
        if (footRow.isNotEmpty()) item { MatchRow("Football", footRow) }
        if (favs.isNotEmpty()) item {
            ChannelRow("Mes favoris", favs, onFocus = { hero = it }) { i -> nav.push(Route.Live(favs, i)) }
        }
        if (recents.isNotEmpty()) item {
            ChannelRow("Regardées récemment", recents, onFocus = { hero = it }) { i -> nav.push(Route.Live(recents, i)) }
        }
        if (resumes.isNotEmpty()) item { ResumeRow(resumes) }
        if (catalog.recentMovies.isNotEmpty()) item {
            RowTitle("Films ajoutés récemment")
            LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(catalog.recentMovies) { m ->
                    Poster(m.poster, m.name, m.rating) { nav.push(Route.MovieDetail(m)) }
                }
            }
        }
        if (catalog.recentSeries.isNotEmpty()) item {
            RowTitle("Séries mises à jour")
            LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(catalog.recentSeries) { s ->
                    Poster(s.poster, s.name, s.rating) { nav.push(Route.SeriesDetail(s)) }
                }
            }
        }
        if (favs.isEmpty() && recents.isEmpty()) item {
            Text(
                "Astuce : dans Direct, garde le bouton OK appuyé sur une chaîne pour l'ajouter à tes favoris.",
                style = T.body,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}

@Composable
fun RowTitle(text: String) {
    Text(text, style = T.h2, modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
}

@Composable
fun HeroChannel(ch: Channel, list: List<Channel>) {
    val nav = LocalNav.current
    val progs = rememberNowNext(ch.id)
    val now = nowSec()
    val cur = progs.firstOrNull { it.isNow(now) }
    val next = progs.firstOrNull { it.start >= (cur?.end ?: now) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Brush.horizontalGradient(listOf(C.bg, C.surface)))
    ) {
        if (ch.logo.isNotBlank()) {
            AsyncImage(
                model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 80.dp).size(160.dp),
                alpha = 0.25f,
            )
        }
        Column(
            Modifier.padding(start = 40.dp, top = 36.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBadge()
                Flag(ch.lang)
                Text(cleanName(ch.name), style = T.body, color = C.muted)
            }
            Text(cur?.title ?: cleanName(ch.name), style = T.hero, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (cur != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${fmtTime(cur.start)} – ${fmtTime(cur.end)}", style = T.body)
                    Bar(cur.progress(now), Modifier.width(130.dp), color = C.text)
                    if (next != null) Text("Ensuite : ${next.title}", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(
                onClick = {
                    val i = list.indexOfFirst { it.id == ch.id }
                    nav.push(Route.Live(if (i >= 0) list else listOf(ch), i.coerceAtLeast(0)))
                },
                colors = ButtonDefaults.colors(containerColor = C.text, contentColor = C.bg, focusedContainerColor = C.text, focusedContentColor = C.bg),
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("▶  Regarder") }
        }
    }
}

@Composable
fun ChannelRow(title: String, list: List<Channel>, onFocus: (Channel) -> Unit = {}, onClick: (Int) -> Unit) {
    val prefs = GliveApp.instance.prefs
    Column {
        RowTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list.size) { i ->
                ChannelCard(list[i], onFocus = { onFocus(list[i]) }, onLongClick = { prefs.toggleFavorite(list[i].id) }) { onClick(i) }
            }
        }
    }
}

@Composable
fun ChannelCard(ch: Channel, onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val progs = rememberNowNext(ch.id)
    val now = nowSec()
    val cur = progs.firstOrNull { it.isNow(now) }
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.width(160.dp).onFocusGained(onFocus),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(80.dp).background(C.surface2), contentAlignment = Alignment.Center) {
                ChannelLogo(ch.logo, ch.name, size = 56.dp)
                Flag(ch.lang, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cleanName(ch.name), style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cur?.title ?: " ", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Bar(cur?.progress(now) ?: 0f, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ResumeRow(resumes: List<com.anis.glive.data.Prefs.Resume>) {
    val nav = LocalNav.current
    val catalog by GliveApp.instance.repo.catalog.collectAsState()
    Column {
        RowTitle("Continuer à regarder")
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(resumes) { r ->
                FocusCard(
                    onClick = {
                        // clé "movie:<id>" ou "ep:<seriesId>:<episodeId>"
                        val parts = r.key.split(':')
                        when (parts[0]) {
                            "movie" -> catalog.movieById[parts.getOrNull(1)?.toIntOrNull() ?: -1]?.let { nav.push(Route.MovieDetail(it)) }
                            "ep" -> catalog.seriesById[parts.getOrNull(1)?.toIntOrNull() ?: -1]?.let { nav.push(Route.SeriesDetail(it)) }
                        }
                    },
                    modifier = Modifier.width(200.dp),
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(112.dp).background(C.surface2), contentAlignment = Alignment.BottomStart) {
                            Text(r.title, style = T.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                            if (r.image.isNotBlank()) AsyncImage(r.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Bar(r.position.toFloat() / r.duration, Modifier.fillMaxWidth().align(Alignment.BottomCenter), height = 4.dp, track = Color(0x55FFFFFF))
                        }
                        Text(
                            "${r.title} · reste ${fmtDuration(r.duration - r.position)}",
                            style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = T.body) }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))
