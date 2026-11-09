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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anis.glive.GliveApp
import com.anis.glive.data.Football
import com.anis.glive.data.Match
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Matchs à venir et en direct, rafraîchis toutes les minutes. */
@Composable
fun rememberMatches(): List<Match> {
    var list by remember { mutableStateOf(emptyList<Match>()) }
    LaunchedEffect(Unit) {
        while (true) {
            list = Football.matches()
            delay(60_000)
        }
    }
    return list
}

fun matchWhen(m: Match): String {
    if (m.live) return m.detail.ifBlank { "En direct" }
    if (m.finished) return "Terminé"
    val c = Calendar.getInstance().apply { timeInMillis = m.time }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val hm = SimpleDateFormat("HH:mm", Locale.FRANCE).format(c.time)
    return when {
        c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "Aujourd'hui · $hm"
        c.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) -> "Demain · $hm"
        else -> SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE).format(c.time).replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun TeamLogo(url: String, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Text(initials(name), style = T.small)
        if (url.isNotBlank()) AsyncImage(url, name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MatchCard(m: Match, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = modifier.width(260.dp).onFocusGained(onFocus)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.league.name, style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(matchWhen(m), style = T.small, color = if (m.live) C.accent else C.muted)
            }
            listOf(Triple(m.home, m.homeLogo, m.homeScore), Triple(m.away, m.awayLogo, m.awayScore)).forEach { (name, logo, score) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TeamLogo(logo, name, 26.dp)
                    Text(name, style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (m.state != "pre") Text(score, style = T.h2)
                }
            }
        }
    }
}

@Composable
fun MatchHero(m: Match) {
    val nav = LocalNav.current
    Box(
        Modifier.fillMaxWidth().height(250.dp)
            .background(Brush.horizontalGradient(listOf(C.bg, C.surface2)))
    ) {
        Column(Modifier.padding(start = 40.dp, top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (m.live) LiveBadge(m.detail.ifBlank { "EN DIRECT" })
                Text("${m.league.name} · ${matchWhen(m)}", style = T.body)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                TeamLogo(m.homeLogo, m.home, 64.dp)
                Text(m.home, style = T.hero)
                Text(if (m.state == "pre") "–" else "${m.homeScore} – ${m.awayScore}", style = T.hero, color = if (m.live) C.accent else C.text)
                Text(m.away, style = T.hero)
                TeamLogo(m.awayLogo, m.away, 64.dp)
            }
            Button(onClick = { nav.push(Route.MatchSources(m)) }, colors = true) {
                Text(if (m.live) "▶  Regarder le match" else "Voir les chaînes")
            }
        }
    }
}

@Composable
fun MatchRow(title: String, matches: List<Match>) {
    val nav = LocalNav.current
    if (matches.isEmpty()) return
    Column {
        RowTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(matches) { m -> MatchCard(m) { nav.push(Route.MatchSources(m)) } }
        }
    }
}

@Composable
fun FootballSection() {
    val matches = rememberMatches()
    val team = GliveApp.instance.prefs.favoriteTeam
    if (matches.isEmpty()) { EmptyState("Chargement des matchs…"); return }
    val live = matches.filter { it.live }
    val mine = matches.filter { it.involves(team) && !it.finished }
    LazyColumn(contentPadding = PaddingValues(top = 30.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Text("Matchs", style = T.h1, modifier = Modifier.padding(start = 40.dp)) }
        if (live.isNotEmpty()) item { MatchRow("En direct", live) }
        if (mine.isNotEmpty()) item { MatchRow(team, mine) }
        Football.leagues.forEach { lg ->
            val l = matches.filter { it.league == lg && !it.finished }
            if (l.isNotEmpty()) item { MatchRow(lg.name, l) }
        }
        val done = matches.filter { it.finished }.sortedByDescending { it.time }.take(20)
        if (done.isNotEmpty()) item { MatchRow("Résultats", done) }
    }
}

/** Les chaînes de ta playlist qui diffusent probablement le match. Haut/Bas dans le lecteur pour changer de source. */
@Composable
fun MatchSourcesScreen(m: Match) {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val langs by app.prefs.langs.collectAsState()
    val favs by app.prefs.favorites.collectAsState()
    val channels = remember(m, catalog, langs) { Football.channelsFor(m, catalog, langs) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(channels.size) { delay(80); runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().background(C.bg).padding(start = 48.dp, top = 36.dp, end = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${m.league.name} · ${matchWhen(m)}", style = T.body)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TeamLogo(m.homeLogo, m.home, 48.dp)
            Text(m.home, style = T.h1)
            Text(if (m.state == "pre") "–" else "${m.homeScore} – ${m.awayScore}", style = T.h1, color = if (m.live) C.accent else C.text)
            Text(m.away, style = T.h1)
            TeamLogo(m.awayLogo, m.away, 48.dp)
        }
        if (m.broadcasts.isNotEmpty()) Text("Diffuseurs officiels : ${m.broadcasts.joinToString(", ")}", style = T.small)
        Text(
            if (channels.isEmpty()) "Aucune chaîne sport trouvée dans ta playlist pour cette compétition."
            else "Chaînes probables dans ta playlist. Dans le lecteur, Haut/Bas passe d'une source à l'autre.",
            style = T.small,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            items(channels.size) { i ->
                ChannelLine(
                    channels[i], channels[i].id in favs,
                    modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                ) { nav.push(Route.Live(channels, i)) }
            }
        }
    }
}
