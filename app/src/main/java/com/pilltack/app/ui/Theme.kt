package com.pilltack.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours sampled from the PillTrack Behance palette. Link/green/red are estimated from the high-fidelity screens. */
object PT {
    val Blue = Color(0xFF164F7A)
    val Ink = Color(0xFF242424)
    val Grey = Color(0xFF999999)
    val Surface = Color(0xFFF3F3F3)
    val Link = Color(0xFF1E6FD9)
    val Green = Color(0xFF2EA44F)
    val Red = Color(0xFFE5383B)
}

/** Design type scale (Satoshi). Add Satoshi .ttf files under res/font and replace SansSerif below. */
object T {
    private val f = FontFamily.SansSerif
    val h1 = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 30.sp)
    val h2 = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    val b1 = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val b2 = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val b3 = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val c1 = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val c2 = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp)
}

@Composable
fun PillTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PT.Blue, onPrimary = Color.White, background = Color.White, surface = Color.White,
            onSurface = PT.Ink, surfaceVariant = PT.Surface, outline = PT.Grey, error = PT.Red
        ),
        typography = Typography(bodyLarge = T.b3, bodyMedium = T.c1, titleLarge = T.h2, labelLarge = T.b2),
        content = content
    )
}
