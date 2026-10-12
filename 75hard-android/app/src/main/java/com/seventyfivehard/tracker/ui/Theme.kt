package com.seventyfivehard.tracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Fire = Color(0xFFFF4D2E)
    val Done = Color(0xFF34C759)
    val Missed = Color(0xFFB3261E)
    val Water = Color(0xFF3BA0FF)
    val Reading = Color(0xFFFFB020)
    val Outdoor = Color(0xFF3DDC97)
    val Photo = Color(0xFFB388FF)
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF18181C)
    val SurfaceHigh = Color(0xFF232329)
    val Muted = Color(0xFF9A9AA3)
}

private val colors = darkColorScheme(
    primary = Palette.Fire,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1610),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Palette.Done,
    onSecondary = Color.Black,
    background = Palette.Background,
    onBackground = Color(0xFFF2F2F5),
    surface = Palette.Background,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF2C2C33),
    outline = Color(0xFF45454D),
    outlineVariant = Color(0xFF2E2E35),
    error = Color(0xFFFF6B60),
)

private val base = Typography()
private val typography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

@Composable
fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
