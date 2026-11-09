package com.anis.glive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.anis.glive.BuildConfig
import com.anis.glive.GliveApp
import com.anis.glive.data.Lang
import com.anis.glive.data.Release
import com.anis.glive.data.Updater
import com.anis.glive.data.normalize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchSection() {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val catalog by app.repo.catalog.collectAsState()
    val favs by app.prefs.favorites.collectAsState()
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(300); debounced = query }
    val q = normalize(debounced.trim())
    var channels by remember { mutableStateOf(emptyList<com.anis.glive.data.Channel>()) }
    var movies by remember { mutableStateOf(emptyList<com.anis.glive.data.Movie>()) }
    var series by remember { mutableStateOf(emptyList<com.anis.glive.data.Series>()) }
    LaunchedEffect(q, catalog) {
        if (q.length < 2) { channels = emptyList(); movies = emptyList(); series = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val c = catalog.channels.filter { normalize(it.name).contains(q) }.take(60)
            val m = catalog.movies.filter { normalize(it.name).contains(q) }.take(60)
            val s = catalog.series.filter { normalize(it.name).contains(q) }.take(60)
            channels = c; movies = m; series = s
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    LazyColumn(contentPadding = PaddingValues(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recherche", style = T.h1)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = TextStyle(color = C.text, fontSize = 18.sp),
                    cursorBrush = SolidColor(C.accent),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box {
                            if (query.isEmpty()) Text("Chaîne, film ou série…", style = T.body, color = C.dim)
                            inner()
                        }
                    },
                    modifier = Modifier
                        .width(560.dp)
                        .background(if (focused) C.surface3 else C.surface, RoundedCornerShape(10.dp))
                        .border(2.dp, if (focused) C.text else C.line, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (channels.isNotEmpty()) item {
            ChannelRow("Chaînes (${channels.size})", channels) { i -> nav.push(Route.Live(channels, i)) }
        }
        if (movies.isNotEmpty()) item {
            RowTitle("Films (${movies.size})")
            LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(movies) { m -> Poster(m.poster, m.name, m.rating) { nav.push(Route.MovieDetail(m)) } }
            }
        }
        if (series.isNotEmpty()) item {
            RowTitle("Séries (${series.size})")
            LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(series) { s -> Poster(s.poster, s.name, s.rating) { nav.push(Route.SeriesDetail(s)) } }
            }
        }
        if (q.length >= 2 && channels.isEmpty() && movies.isEmpty() && series.isEmpty()) item {
            Text("Aucun résultat pour « $debounced »", style = T.body, modifier = Modifier.padding(horizontal = 40.dp))
        }
    }
}

@Composable
fun SettingsSection(onLogout: () -> Unit) {
    val app = GliveApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog by app.repo.catalog.collectAsState()
    val langs by app.prefs.langs.collectAsState()
    val hidden by app.prefs.hidden.collectAsState()
    var kind by remember { mutableStateOf("live") }
    var update by remember { mutableStateOf<Release?>(null) }
    var updateMsg by remember { mutableStateOf<String?>(null) }

    val presentLangs = remember(catalog) {
        (catalog.liveCats + catalog.vodCats + catalog.seriesCats).map { it.lang }.distinct().sortedBy { it.ordinal }
    }
    val cats = remember(catalog, kind, langs) {
        val all = when (kind) { "vod" -> catalog.vodCats; "series" -> catalog.seriesCats; else -> catalog.liveCats }
        all.filter { langs.isEmpty() || it.lang in langs }
    }

    LazyColumn(contentPadding = PaddingValues(40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Réglages", style = T.h1) }
        item {
            Text("Thème", style = T.h2)
            Gap(8)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Themes.all) { p ->
                    val sel = Themes.current.id == p.id
                    FocusCard(
                        onClick = { Themes.select(p.id); app.prefs.theme = p.id },
                        color = p.surface, focusedColor = p.surface3,
                        modifier = Modifier.width(150.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                androidx.compose.foundation.layout.Box(Modifier.width(22.dp).height(22.dp).background(p.bg, RoundedCornerShape(50)))
                                androidx.compose.foundation.layout.Box(Modifier.width(22.dp).height(22.dp).background(p.accent, RoundedCornerShape(50)))
                            }
                            Text(p.name + if (sel) "  ✓" else "", style = T.label)
                        }
                    }
                }
            }
            Gap(10)
        }
        item {
            Text("Langues affichées", style = T.h2)
            Text("Rien de coché = toutes les langues. Le reste est caché partout dans l'app.", style = T.small)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(presentLangs) { l ->
                    FocusCard(
                        onClick = { app.prefs.toggleLang(l) },
                        shape = RoundedCornerShape(50),
                        color = if (l in langs) C.text else C.surface,
                        focusedColor = if (l in langs) C.text else C.surface3,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Flag(l)
                            Text(l.label, style = T.label, color = if (l in langs) C.bg else C.text2)
                        }
                    }
                }
            }
        }
        item {
            Gap(10)
            Text("Groupes visibles", style = T.h2)
            Text("OK sur un groupe pour le cacher ou le réafficher.", style = T.small)
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Direct", kind == "live") { kind = "live" }
                Pill("Films", kind == "vod") { kind = "vod" }
                Pill("Séries", kind == "series") { kind = "series" }
            }
        }
        items(cats) { c ->
            val key = "$kind:${c.id}"
            val isHidden = key in hidden
            FocusCard(onClick = { app.prefs.toggleHidden(key) }, modifier = Modifier.width(560.dp), focusedScale = 1.02f) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Flag(c.lang)
                    Text(c.name, style = T.label, color = if (isHidden) C.dim else C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (isHidden) "Caché" else "Visible", style = T.small, color = if (isHidden) C.dim else C.text2)
                }
            }
        }
        item {
            Gap(16)
            Text("Compte et application", style = T.h2)
            Text("Serveur : ${app.prefs.account?.server ?: ""} · Utilisateur : ${app.prefs.account?.user ?: ""}", style = T.small)
            Text("Version ${BuildConfig.VERSION_NAME} · ${catalog.channels.size} chaînes, ${catalog.movies.size} films, ${catalog.series.size} séries", style = T.small)
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { app.repo.load(force = true) }) { Text("Actualiser les listes") }
                Button(onClick = {
                    scope.launch {
                        updateMsg = "Recherche…"
                        val r = Updater.latest()
                        update = r?.takeIf { Updater.isNewer(it) }
                        updateMsg = when {
                            r == null -> "Impossible de vérifier (dépôt privé ou pas de réseau)"
                            update == null -> "Tu as la dernière version"
                            else -> "Nouvelle version disponible : ${r.name}"
                        }
                    }
                }) { Text("Mises à jour") }
                update?.let { r ->
                    Button(onClick = {
                        scope.launch {
                            updateMsg = "Téléchargement…"
                            val ok = Updater.downloadAndInstall(context, r) { p -> updateMsg = "Téléchargement ${(p * 100).toInt()} %" }
                            if (!ok) updateMsg = "Échec du téléchargement"
                        }
                    }) { Text("Installer") }
                }
                Button(onClick = onLogout) { Text("Se déconnecter") }
            }
            updateMsg?.let { Text(it, style = T.small, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
