package com.anis.glive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.anis.glive.GliveApp
import com.anis.glive.data.Catalog
import com.anis.glive.data.Channel
import com.anis.glive.data.Lang

const val FAV_GROUP = "__fav__"
const val ALL_GROUP = "__all__"

data class Group(val id: String, val name: String, val lang: Lang?, val count: Int)

@Composable
fun rememberLiveGroups(catalog: Catalog): List<Group> {
    val app = GliveApp.instance
    val favs by app.prefs.favorites.collectAsState()
    val langs by app.prefs.langs.collectAsState()
    val hidden by app.prefs.hidden.collectAsState()
    return remember(catalog, favs, langs, hidden) {
        val cats = app.repo.visibleLiveCats(catalog)
        buildList {
            add(Group(FAV_GROUP, "Favoris", null, favs.size))
            cats.forEach { add(Group(it.id, cleanName(it.name), it.lang, catalog.channelsByCat[it.id]?.size ?: 0)) }
        }
    }
}

fun channelsOf(group: String, catalog: Catalog, favs: Set<Int>): List<Channel> = when (group) {
    FAV_GROUP -> favs.mapNotNull { catalog.channelById[it] }.sortedBy { it.num }
    ALL_GROUP -> catalog.channels
    else -> catalog.channelsByCat[group].orEmpty()
}

@Composable
fun GroupColumn(groups: List<Group>, selected: String, onSelect: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(pending) { pending?.let { kotlinx.coroutines.delay(260); onSelect(it) } }
    LazyColumn(
        Modifier.width(210.dp).fillMaxHeight().background(C.bg),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(groups) { g ->
            FocusCard(
                onClick = { onSelect(g.id) },
                modifier = Modifier.fillMaxWidth().onFocusGained { pending = g.id },
                color = if (g.id == selected) C.surface2 else C.bg,
                focusedScale = 1.02f,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (g.lang != null) Flag(g.lang) else Text("★", style = T.label, color = C.star)
                    Text(g.name, style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${g.count}", style = T.small)
                }
            }
        }
    }
}

@Composable
fun LiveSection() {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val favs by app.prefs.favorites.collectAsState()
    val groups = rememberLiveGroups(catalog)
    var selected by rememberSaveable { mutableStateOf(if (favs.isNotEmpty()) FAV_GROUP else groups.getOrNull(1)?.id ?: FAV_GROUP) }
    val channels = remember(selected, catalog, favs) { channelsOf(selected, catalog, favs) }

    Row(Modifier.fillMaxSize()) {
        GroupColumn(groups, selected) { selected = it }
        if (channels.isEmpty()) {
            EmptyState(if (selected == FAV_GROUP) "Aucun favori. Garde OK appuyé sur une chaîne pour l'ajouter." else "Aucune chaîne")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp, horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(channels.size) { i ->
                    ChannelLine(channels[i], favs.contains(channels[i].id),
                        onLongClick = { app.prefs.toggleFavorite(channels[i].id) }) {
                        nav.push(Route.Live(channels, i))
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelLine(
    ch: Channel,
    fav: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val progs = rememberNowNext(ch.id)
    val now = nowSec()
    val cur = progs.firstOrNull { it.isNow(now) }
    val next = progs.firstOrNull { it.start >= (cur?.end ?: now) }
    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusGained(onFocus),
        focusedScale = 1.02f,
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (compact) 58.dp else 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (ch.num > 0) "${ch.num}" else "", style = T.small, maxLines = 1, modifier = Modifier.width(36.dp))
            ChannelLogo(ch.logo, ch.name, size = if (compact) 40.dp else 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Flag(ch.lang, width = 15.dp)
                    Text(cleanName(ch.name), style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (fav) Text("★", style = T.small, color = C.star)
                }
                if (compact) {
                    Text(cur?.title ?: "Pas de programme", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (ch.archive) {
                    Text("Replay ${ch.archiveDays} jours", style = T.small, maxLines = 1)
                }
            }
            if (!compact) {
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(cur?.title ?: "Pas de programme", style = T.label, color = if (cur != null) C.text else C.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cur != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(fmtTime(cur.start), style = T.small, maxLines = 1)
                            Bar(cur.progress(now), Modifier.weight(1f))
                            Text(fmtTime(cur.end), style = T.small, maxLines = 1)
                        }
                        if (next != null) Text("Ensuite : ${next.title}", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun GuideSection() {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val favs by app.prefs.favorites.collectAsState()
    val groups = rememberLiveGroups(catalog)
    var selected by rememberSaveable { mutableStateOf(if (favs.isNotEmpty()) FAV_GROUP else groups.getOrNull(1)?.id ?: FAV_GROUP) }
    val channels = remember(selected, catalog, favs) { channelsOf(selected, catalog, favs) }
    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    val ch = focusedCh?.takeIf { c -> channels.any { it.id == c.id } } ?: channels.firstOrNull()

    Row(Modifier.fillMaxSize()) {
        GroupColumn(groups, selected) { selected = it }
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 24.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels.size) { i ->
                val c = channels[i]
                FocusCard(
                    onClick = { nav.push(Route.Live(channels, i)) },
                    modifier = Modifier.fillMaxWidth().onFocusGained { focusedCh = c },
                    color = if (c.id == ch?.id) C.surface2 else C.surface,
                    focusedScale = 1.02f,
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChannelLogo(c.logo, c.name, size = 36.dp)
                        Flag(c.lang, width = 15.dp)
                        Text(cleanName(c.name), style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (ch == null) EmptyState("Aucune chaîne") else ProgramList(ch, channels)
    }
}

@Composable
fun ProgramList(ch: Channel, channels: List<Channel>) {
    val nav = LocalNav.current
    val progs = rememberPrograms(ch.id)
    val now = nowSec()
    val startIndex = remember(progs) { progs.indexOfFirst { it.isNow(now) }.coerceAtLeast(0) }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 24.dp, end = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(cleanName(ch.name), style = T.h1)
            if (ch.archive) Text("Replay disponible ${ch.archiveDays} jours", style = T.small, color = C.accent)
        }
        Gap(14)
        if (progs.isEmpty()) {
            Text("Pas de guide pour cette chaîne.", style = T.body)
            return@Column
        }
        val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(progs) { p ->
                val live = p.isNow(now)
                val replay = p.isPast(now) && (p.hasArchive || ch.archive)
                FocusCard(
                    onClick = {
                        when {
                            live -> nav.push(Route.Live(channels, channels.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)))
                            replay -> nav.push(Route.Catchup(ch, p))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (live) C.surface2 else C.surface,
                    focusedScale = 1.02f,
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(fmtTime(p.start), style = T.label, color = if (p.isPast(now) && !live) C.dim else C.text, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (live) LiveBadge()
                                if (replay) ReplayBadge()
                                Text(p.title, style = T.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (live) Bar(p.progress(now), Modifier.width(200.dp))
                            if (p.description.isNotBlank()) Text(p.description, style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${p.durationMin} min", style = T.small)
                    }
                }
            }
        }
    }
}

@Composable
fun ReplayBadge() {
    Box(
        Modifier
            .background(C.surface3, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text("↺ Revoir", style = T.small, color = C.text) }
}
