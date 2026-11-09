package com.anis.glive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

data class Palette(
    val id: String,
    val name: String,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val accent: Color,
)

object Themes {
    val all = listOf(
        Palette("bleu", "Nuit bleue", Color(0xFF0B1220), Color(0xFF131C30), Color(0xFF1B2742), Color(0xFF263557), Color(0xFF2A3656), Color(0xFF3D8BFF)),
        Palette("rouge", "Noir et rouge", Color(0xFF000000), Color(0xFF141414), Color(0xFF1F1F1F), Color(0xFF2C2C2C), Color(0xFF333333), Color(0xFFE50914)),
        Palette("vert", "Émeraude", Color(0xFF07110E), Color(0xFF0F1C18), Color(0xFF16271F), Color(0xFF20372B), Color(0xFF23392E), Color(0xFF22C55E)),
        Palette("braise", "Braise", Color(0xFF0A0C10), Color(0xFF14171E), Color(0xFF1C2029), Color(0xFF262B36), Color(0xFF2B303B), Color(0xFFFF5A36)),
        Palette("violet", "Violet", Color(0xFF0D0A16), Color(0xFF171226), Color(0xFF211A36), Color(0xFF2D2449), Color(0xFF2E2647), Color(0xFF8B5CF6)),
    )
    var current by mutableStateOf(all.first())
    fun select(id: String) { current = all.firstOrNull { it.id == id } ?: all.first() }
}

object C {
    val bg get() = Themes.current.bg
    val surface get() = Themes.current.surface
    val surface2 get() = Themes.current.surface2
    val surface3 get() = Themes.current.surface3
    val line get() = Themes.current.line
    val accent get() = Themes.current.accent
    val text = Color(0xFFF4F5F7)
    val text2 = Color(0xFFC9CDD6)
    val muted = Color(0xFFA3A9B6)
    val dim = Color(0xFF8A91A0)
    val star = Color(0xFFF4C542)
}

object T {
    val hero = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = C.text, lineHeight = 38.sp)
    val h1 = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.text)
    val h2 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    val body = TextStyle(fontSize = 13.sp, color = C.text2, lineHeight = 19.sp)
    val label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    val small = TextStyle(fontSize = 11.sp, color = C.muted)
}

@Composable
fun GliveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.text,
            onPrimary = C.bg,
            background = C.bg,
            onBackground = C.text,
            surface = C.surface,
            onSurface = C.text,
            surfaceVariant = C.surface2,
            onSurfaceVariant = C.text2,
            border = C.line,
        ),
        content = content,
    )
}
