package com.iyftv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Color(0xFFE5484D),
    onPrimary = Color.White,
    background = Color(0xFF0E0F13),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFECEDEE),
)

@Composable
fun IyfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
