package com.anis.glive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.anis.glive.GliveApp
import com.anis.glive.data.Channel
import com.anis.glive.data.Episode
import com.anis.glive.data.LoadState
import com.anis.glive.data.Movie
import com.anis.glive.data.Program
import com.anis.glive.data.Series

sealed class Route {
    data object Home : Route()
    data class Live(val channels: List<Channel>, val index: Int) : Route()
    data class Catchup(val channel: Channel, val program: Program) : Route()
    data class Vod(
        val url: String,
        val title: String,
        val subtitle: String,
        val resumeKey: String,
        val image: String,
        val episodes: List<Episode> = emptyList(),
        val episodeIndex: Int = -1,
        val seriesId: Int = 0,
    ) : Route()
    data class MovieDetail(val movie: Movie) : Route()
    data class SeriesDetail(val series: Series) : Route()
    data class MatchSources(val match: com.anis.glive.data.Match) : Route()
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    fun push(r: Route) { stack.add(r) }
    fun replace(r: Route) { if (stack.size > 1) stack.removeAt(stack.lastIndex); stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

val LocalNav = staticCompositionLocalOf { Nav() }

class MainActivity : ComponentActivity() {
    /** Retour géré en un seul appui : Compose ne doit pas l'utiliser pour retirer le focus. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK || event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Connexion automatique pour les tests sur émulateur
        intent?.getStringExtra("server")?.let { server ->
            val app = GliveApp.instance
            app.prefs.account = com.anis.glive.data.Account(server, intent.getStringExtra("user") ?: "", intent.getStringExtra("pass") ?: "")
            app.repo.start()
        }
        setContent { GliveTheme { App() } }
    }
}

@Composable
fun App() {
    val app = GliveApp.instance
    val repo = app.repo
    var loggedIn by remember { mutableStateOf(app.prefs.account != null) }
    val nav = remember { Nav() }
    var crash by remember { mutableStateOf(app.crashFile().takeIf { it.exists() }?.readText()) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        crash?.let { text ->
            CrashScreen(text) { app.crashFile().delete(); crash = null }
            return@Box
        }
        if (!loggedIn) {
            LoginScreen(onDone = {
                loggedIn = true
                repo.start()
            })
            return@Box
        }
        val state by repo.state.collectAsState()
        when (val s = state) {
            is LoadState.Idle, is LoadState.Loading -> Splash((s as? LoadState.Loading)?.step ?: "Connexion…")
            is LoadState.Error -> ErrorScreen(
                message = s.message,
                onRetry = { repo.load(force = true) },
                onLogout = { repo.logout(); loggedIn = false },
            )
            is LoadState.Ready -> CompositionLocalProvider(LocalNav provides nav) {
                BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
                when (val r = nav.stack.last()) {
                    is Route.Home -> HomeScreen(onLogout = { repo.logout(); nav.stack.clear(); nav.stack.add(Route.Home); loggedIn = false })
                    is Route.Live -> PlayerScreen(PlayerMode.Live(r.channels, r.index))
                    is Route.Catchup -> PlayerScreen(PlayerMode.Catchup(r.channel, r.program))
                    is Route.Vod -> PlayerScreen(PlayerMode.Vod(r))
                    is Route.MovieDetail -> MovieDetailScreen(r.movie)
                    is Route.SeriesDetail -> SeriesDetailScreen(r.series)
                    is Route.MatchSources -> MatchSourcesScreen(r.match)
                }
            }
        }
    }
}

@Composable
fun Splash(step: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Glive", style = T.hero, color = C.accent)
        Text(step, style = T.body, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onLogout: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Impossible de charger tes chaînes", style = T.h1)
        Text(message, style = T.body, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(fr)) { Text("Réessayer") }
            Button(onClick = onLogout) { Text("Changer de compte") }
        }
    }
}

@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Glive a planté la dernière fois", style = T.h1)
        Text("Prends cet écran en photo et envoie-la à Claude, puis appuie sur OK.", style = T.body)
        Button(onClick = onContinue, modifier = Modifier.focusRequester(fr), colors = true) { Text("OK, continuer") }
        Text(text.take(2500), style = T.small.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)), color = C.text2)
    }
}
