package com.trading.terminal.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TerminalBackground = Color(0xFF08090C)
val TerminalSurface = Color(0xFF12141A)
val TerminalPanel = Color(0xFF181B22)
val TerminalBorder = Color(0xFF222631)
val TerminalGrid = Color(0xFF1E222D)

// Trading Action Colors
val BullGreen = Color(0xFF00E676)
val BearRed = Color(0xFFFF1744)
val ElectricEmerald = Color(0xFF00E676)
val VibrantCrimson = Color(0xFFFF1744)

// Bookmap Heatmap Accents
val HeatmapCyan = Color(0xFF00E5FF)
val HeatmapAmber = Color(0xFFFF9100)
val HeatmapHotWhite = Color(0xFFFFFFFF)

// Text
val TextPrimary = Color(0xFFE0E3EB)
val TextSecondary = Color(0xFF787B86)
val TextMuted = Color(0xFF4A4E5A)

private val TerminalColorScheme = darkColorScheme(
    background = TerminalBackground,
    surface = TerminalSurface,
    surfaceVariant = TerminalPanel,
    primary = ElectricEmerald,
    secondary = HeatmapCyan,
    error = VibrantCrimson,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        content = content
    )
}
