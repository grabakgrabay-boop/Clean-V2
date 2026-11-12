package com.example.cleaner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF00A88F)
val TealDark = Color(0xFF00695C)
val Danger = Color(0xFFD32F2F)

private val scheme = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    secondary = Color(0xFF2979FF),
    background = Color(0xFFF3F6F9),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun CleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
