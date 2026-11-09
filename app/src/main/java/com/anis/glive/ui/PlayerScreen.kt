package com.anis.glive.ui

import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.anis.glive.GliveApp
import com.anis.glive.data.Channel
import com.anis.glive.data.Program
import kotlinx.coroutines.delay

sealed class PlayerMode {
    data class Live(val channels: List<Channel>, val index: Int) : PlayerMode()
    data class Catchup(val channel: Channel, val program: Program) : PlayerMode()
    data class Vod(val route: Route.Vod) : PlayerMode()
}

@OptIn(UnstableApi::class)
@Composable
fun rememberExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val prefs = GliveApp.instance.prefs
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(prefs.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_200, 2_500)
            .build()
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .setLoadControl(loadControl)
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun PlayerScreen(mode: PlayerMode) {
    when (mode) {
        is PlayerMode.Live -> LivePlayer(mode.channels, mode.index)
        is PlayerMode.Catchup -> VodPlayer(
            url = GliveApp.instance.repo.xtream!!.catchupUrl(mode.channel.id, mode.program.startRaw, mode.program.durationMin + 2),
            title = mode.program.title,
            subtitle = "Replay · ${cleanName(mode.channel.name)} · ${fmtTime(mode.program.start)}",
            resumeKey = null, image = "", route = null,
        )
        is PlayerMode.Vod -> VodPlayer(mode.route.url, mode.route.title, mode.route.subtitle, mode.route.resumeKey, mode.route.image, mode.route)
    }
}

@Composable
fun PlayerError(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Color(0xE60A0C10), RoundedCornerShape(12.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Flux indisponible", style = T.h2)
            Text(message, style = T.small)
            Text("OK pour réessayer", style = T.small, color = C.text2)
        }
    }
}

@Composable
fun LivePlayer(channels: List<Channel>, startIndex: Int) {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val player = rememberExoPlayer()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showList by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var numberBuffer by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val ch = channels.getOrNull(index)

    fun play(i: Int) {
        val c = channels.getOrNull(i) ?: return
        error = null
        retries = 0
        player.setMediaItem(MediaItem.fromUri(app.repo.xtream!!.liveUrl(c.id)))
        player.prepare()
        player.play()
        app.prefs.lastChannel = c.id
        app.prefs.addRecent(c.id)
    }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
        showInfo = true
        infoTick++
    }

    LaunchedEffect(index) {
        delay(250) // évite de charger chaque chaîne quand on zappe vite
        play(index)
    }
    LaunchedEffect(infoTick, showInfo) {
        if (showInfo) { delay(5000); showInfo = false }
    }
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isNotEmpty()) {
            delay(1500)
            val n = numberBuffer.toIntOrNull()
            numberBuffer = ""
            val i = channels.indexOfFirst { it.num == n }
            if (i >= 0) { index = i; showInfo = true; infoTick++ }
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (retries < 2) {
                    retries++
                    player.prepare()
                    player.play()
                } else error = e.errorCodeName
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(showList) { if (!showList) runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (showList) return@onPreviewKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); true }
                    AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); true }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER -> {
                        if (error != null) { play(index); true } else { showList = true; true }
                    }
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_INFO -> { showInfo = !showInfo; infoTick++; true }
                    AKeyEvent.KEYCODE_MENU -> { ch?.let { app.prefs.toggleFavorite(it.id) }; showInfo = true; infoTick++; true }
                    in AKeyEvent.KEYCODE_0..AKeyEvent.KEYCODE_9 -> {
                        numberBuffer += (ev.key.nativeKeyCode - AKeyEvent.KEYCODE_0).toString(); true
                    }
                    else -> false
                }
            }
    ) {
        VideoSurface(player)
        PlayerError(error)
        if (numberBuffer.isNotEmpty()) {
            Text(
                numberBuffer, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = C.text,
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
                    .background(Color(0xCC0A0C10), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(showInfo && !showList && ch != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            if (ch != null) LiveInfoBar(ch, index, channels.size)
        }
        if (showList) {
            ChannelPanel(
                channels = channels,
                current = index,
                onPick = { i -> index = i; showList = false; showInfo = true; infoTick++ },
                onClose = { showList = false },
                onCatchup = { c, p -> showList = false; nav.push(Route.Catchup(c, p)) },
            )
        }
    }
}

@Composable
fun LiveInfoBar(ch: Channel, index: Int, total: Int) {
    val favs by GliveApp.instance.prefs.favorites.collectAsState()
    val progs = rememberPrograms(ch.id)
    val now = nowSec()
    val cur = progs.firstOrNull { it.isNow(now) }
    val next = progs.firstOrNull { it.start >= (cur?.end ?: now) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF20A0C10))))
            .padding(start = 40.dp, end = 40.dp, top = 60.dp, bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChannelLogo(ch.logo, ch.name, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ch.num > 0) "${ch.num}" else "${index + 1}", style = T.h2, color = C.muted)
                    Text(cleanName(ch.name), style = T.h2)
                    Flag(ch.lang, width = 20.dp)
                    Text(ch.lang.label, style = T.small)
                    if (ch.id in favs) Text("★", style = T.h2, color = C.star)
                }
                Text(cur?.title ?: "Pas de programme", style = T.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cur != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(fmtTime(cur.start), style = T.small)
                    Bar(cur.progress(now), Modifier.width(320.dp), color = C.text)
                    Text(fmtTime(cur.end), style = T.small)
                    if (next != null) Text("Ensuite : ${next.title}", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Haut/Bas : zapper", style = T.small)
                Text("OK : liste des chaînes", style = T.small)
                Text("Menu : favori", style = T.small)
            }
        }
    }
}

@Composable
fun ChannelPanel(
    channels: List<Channel>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    onCatchup: (Channel, Program) -> Unit,
) {
    val favs by GliveApp.instance.prefs.favorites.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val first = remember { FocusRequester() }
    var focused by remember { mutableIntStateOf(current) }
    LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
    androidx.activity.compose.BackHandler { onClose() }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.width(430.dp).fillMaxHeight().background(Color(0xF00A0C10)),
            contentPadding = PaddingValues(vertical = 24.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels.size, key = { channels[it].id }) { i ->
                val c = channels[i]
                ChannelLine(
                    c, c.id in favs,
                    modifier = if (i == current) Modifier.focusRequester(first) else Modifier,
                    onFocus = { focused = i },
                    onLongClick = { GliveApp.instance.prefs.toggleFavorite(c.id) },
                ) { onPick(i) }
            }
        }
        val fc = channels.getOrNull(focused)
        if (fc != null) {
            Box(Modifier.width(380.dp).fillMaxHeight().background(Color(0xE60A0C10))) {
                MiniGuide(fc, onCatchup)
            }
        }
    }
}

@Composable
fun MiniGuide(ch: Channel, onCatchup: (Channel, Program) -> Unit) {
    val progs = rememberPrograms(ch.id)
    val now = nowSec()
    val list = remember(progs) {
        val i = progs.indexOfFirst { it.isNow(now) }.coerceAtLeast(0)
        progs.drop((i - 6).coerceAtLeast(0)).take(14)
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(cleanName(ch.name), style = T.h2)
        if (list.isEmpty()) Text("Pas de guide", style = T.small)
        list.forEach { p ->
            val live = p.isNow(now)
            val replay = p.isPast(now) && (p.hasArchive || ch.archive)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(p.start), style = T.small, color = if (live) C.accent else C.muted, modifier = Modifier.width(40.dp))
                Text(p.title, style = T.label, color = if (p.isPast(now) && !live) C.dim else C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (replay) Text("↺", style = T.small, color = C.text2)
            }
        }
        Text("Le replay s'ouvre depuis le Guide TV.", style = T.small, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun VodPlayer(url: String, title: String, subtitle: String, resumeKey: String?, image: String, route: Route.Vod?) {
    val app = GliveApp.instance
    val nav = LocalNav.current
    val player = rememberExoPlayer()
    var showUi by remember { mutableStateOf(true) }
    var uiTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        val start = resumeKey?.let { app.prefs.resumeFor(it)?.position } ?: 0L
        player.prepare()
        if (start > 0) player.seekTo(start)
        player.play()
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(500)
            position = player.currentPosition
            duration = player.duration.takeIf { it != MC.TIME_UNSET && it > 0 } ?: 0L
            playing = player.isPlaying
            if (++n % 20 == 0 && resumeKey != null) app.prefs.saveResume(resumeKey, title, image, position, duration)
        }
    }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED) ended = true
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            if (resumeKey != null) app.prefs.saveResume(resumeKey, title, image, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }
    LaunchedEffect(uiTick, showUi, playing) {
        if (showUi && playing) { delay(4500); showUi = false }
    }
    val nextEpisode = route?.episodes?.getOrNull(route.episodeIndex + 1)
    LaunchedEffect(ended) {
        if (ended && route != null && nextEpisode != null) {
            nav.replace(episodeRoute(route.seriesId, route.subtitle, route.image, route.episodes, route.episodeIndex + 1))
        }
    }

    fun seek(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
        showUi = true; uiTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (error != null) { player.prepare(); player.play() }
                        else if (!showUi) { showUi = true }
                        else { if (player.isPlaying) player.pause() else player.play() }
                        uiTick++; true
                    }
                    AKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                    AKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); showUi = true; true }
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-10_000); true }
                    AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(30_000); true }
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> { showUi = !showUi; uiTick++; true }
                    AKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (route != null && nextEpisode != null) nav.replace(episodeRoute(route.seriesId, route.subtitle, route.image, route.episodes, route.episodeIndex + 1))
                        true
                    }
                    else -> false
                }
            }
    ) {
        VideoSurface(player)
        PlayerError(error)
        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xD90A0C10), Color.Transparent)))
                        .padding(start = 40.dp, top = 28.dp, bottom = 40.dp)
                ) {
                    Text(subtitle, style = T.body)
                    Text(title, style = T.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF20A0C10))))
                        .padding(start = 40.dp, end = 40.dp, top = 50.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(fmtClock(position), style = T.label)
                        Bar(if (duration > 0) position.toFloat() / duration else 0f, Modifier.weight(1f), height = 5.dp, track = Color(0x40FFFFFF))
                        Text(if (duration > 0) "-" + fmtClock(duration - position) else "", style = T.label, color = C.text2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(if (playing) "❚❚  Pause" else "▶  Lecture", style = T.h2)
                        Text("Gauche : -10 s · Droite : +30 s · OK : pause", style = T.small)
                        if (nextEpisode != null) Text("Suivant : É${nextEpisode.number} ${nextEpisode.title}", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
