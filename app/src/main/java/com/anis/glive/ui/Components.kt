package com.anis.glive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anis.glive.data.Lang
import java.util.Locale

@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    color: Color = C.surface,
    focusedColor: Color = C.surface3,
    focusedScale: Float = 1.05f,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            contentColor = C.text,
            focusedContainerColor = focusedColor,
            focusedContentColor = C.text,
            pressedContainerColor = focusedColor,
            pressedContentColor = C.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, C.text), shape = shape),
        ),
        content = content,
    )
}

@Composable
fun Flag(lang: Lang, modifier: Modifier = Modifier, width: Dp = 18.dp) {
    val h = width * 2 / 3
    Canvas(
        modifier
            .size(width, h)
            .clip(RoundedCornerShape(2.dp))
    ) {
        val w = size.width
        val hh = size.height
        fun rect(x: Float, y: Float, rw: Float, rh: Float, c: Color) =
            drawRect(c, Offset(x, y), Size(rw, rh))
        when (lang) {
            Lang.FR -> { rect(0f, 0f, w / 3, hh, Color(0xFF2B4EA2)); rect(w / 3, 0f, w / 3, hh, Color.White); rect(2 * w / 3, 0f, w / 3, hh, Color(0xFFE3342F)) }
            Lang.IT -> { rect(0f, 0f, w / 3, hh, Color(0xFF009246)); rect(w / 3, 0f, w / 3, hh, Color.White); rect(2 * w / 3, 0f, w / 3, hh, Color(0xFFCE2B37)) }
            Lang.DE -> { rect(0f, 0f, w, hh / 3, Color.Black); rect(0f, hh / 3, w, hh / 3, Color(0xFFDD0000)); rect(0f, 2 * hh / 3, w, hh / 3, Color(0xFFFFCE00)) }
            Lang.NL -> { rect(0f, 0f, w, hh / 3, Color(0xFFAE1C28)); rect(0f, hh / 3, w, hh / 3, Color.White); rect(0f, 2 * hh / 3, w, hh / 3, Color(0xFF21468B)) }
            Lang.ES -> { rect(0f, 0f, w, hh, Color(0xFFC60B1E)); rect(0f, hh / 4, w, hh / 2, Color(0xFFFFC400)) }
            Lang.PT -> { rect(0f, 0f, w * 0.4f, hh, Color(0xFF046A38)); rect(w * 0.4f, 0f, w * 0.6f, hh, Color(0xFFDA291C)) }
            Lang.EN -> { rect(0f, 0f, w, hh, Color.White); rect(0f, hh * 0.38f, w, hh * 0.24f, Color(0xFFCE1124)); rect(w * 0.42f, 0f, w * 0.16f, hh, Color(0xFFCE1124)) }
            Lang.CA -> { rect(0f, 0f, w, hh, Color.White); rect(0f, 0f, w / 4, hh, Color(0xFFD52B1E)); rect(3 * w / 4, 0f, w / 4, hh, Color(0xFFD52B1E)); rect(w * 0.42f, hh * 0.3f, w * 0.16f, hh * 0.4f, Color(0xFFD52B1E)) }
            Lang.TR -> {
                rect(0f, 0f, w, hh, Color(0xFFE30A17))
                drawCircle(Color.White, hh * 0.3f, Offset(w * 0.4f, hh / 2))
                drawCircle(Color(0xFFE30A17), hh * 0.24f, Offset(w * 0.46f, hh / 2))
            }
            Lang.AR -> {
                rect(0f, 0f, w / 2, hh, Color(0xFF1F7A3E)); rect(w / 2, 0f, w / 2, hh, Color.White)
                drawArc(Color(0xFFD52B1E), 45f, 270f, false, Offset(w / 2 - hh * 0.28f, hh * 0.22f), Size(hh * 0.56f, hh * 0.56f), style = Stroke(hh * 0.1f))
                drawCircle(Color(0xFFD52B1E), hh * 0.08f, Offset(w / 2 + hh * 0.2f, hh / 2))
            }
            Lang.OTHER -> rect(0f, 0f, w, hh, Color(0xFF3A4050))
        }
    }
}

@Composable
fun ChannelLogo(url: String, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(C.surface3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            fontSize = (size.value * 0.3f).sp,
            fontWeight = FontWeight.Bold,
            color = C.text2,
            textAlign = TextAlign.Center,
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.08f),
            )
        }
    }
}

fun initials(name: String): String {
    val clean = name.replace(Regex("^[A-Z]{2,3}\\s*[|:\\-]\\s*"), "")
    return clean.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT).ifEmpty { "?" }
}

/** Retire le préfixe de langue type "FR | " pour l'affichage. */
fun cleanName(name: String): String =
    name.replace(Regex("^\\s*[|\\[(]?\\s*[A-Za-z]{2,3}\\s*[|\\])\\-:]+\\s*"), "").trim().ifEmpty { name }

@Composable
fun Poster(
    url: String,
    title: String,
    rating: Double,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .size(width, width * 1.5f)
            .onFocusGained(onFocus),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.08f,
    ) {
        Box(Modifier.fillMaxSize().background(C.surface2), contentAlignment = Alignment.BottomStart) {
            Text(
                title,
                style = T.label,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
            if (url.isNotBlank()) {
                AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (rating > 0) {
                RatingBadge(rating, Modifier.align(Alignment.TopEnd).padding(5.dp))
            }
        }
    }
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC0A0C10))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("★", fontSize = 10.sp, color = C.star)
        Text(String.format(Locale.FRANCE, "%.1f", rating), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.text)
    }
}

@Composable
fun LiveBadge(text: String = "EN DIRECT") {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(C.accent)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(C.bg))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.bg)
    }
}

@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier, color: Color = C.accent, track: Color = C.line, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(2.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) C.text else C.surface,
        focusedColor = if (selected) C.text else C.surface3,
    ) {
        Text(
            text,
            style = T.label,
            color = if (selected) C.bg else C.text2,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

fun fmtTime(sec: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = sec * 1000 }
    return String.format(Locale.FRANCE, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

fun fmtDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
}

fun Modifier.onFocusGained(f: () -> Unit): Modifier =
    this.then(Modifier.onFocusChanged { if (it.isFocused) f() })
