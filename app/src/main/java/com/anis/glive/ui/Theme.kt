package com.anis.glive.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object C {
    val bg = Color(0xFF0A0C10)
    val surface = Color(0xFF14171E)
    val surface2 = Color(0xFF1C2029)
    val surface3 = Color(0xFF262B36)
    val line = Color(0xFF2B303B)
    val text = Color(0xFFF4F5F7)
    val text2 = Color(0xFFC9CDD6)
    val muted = Color(0xFFA3A9B6)
    val dim = Color(0xFF8A91A0)
    val accent = Color(0xFFFF5A36)
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
        ),
        content = content,
    )
}
