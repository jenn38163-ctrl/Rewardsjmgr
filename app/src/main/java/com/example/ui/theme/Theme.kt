package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = Color(0xFF00382E),
    primaryContainer = TealAccentContainer,
    onPrimaryContainer = TealAccent,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = PurpleBadge,
    onSecondaryContainer = Color(0xFFE0D8FF),
    tertiary = AmberAccent,
    onTertiary = Color.Black,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberAccent,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    outlineVariant = DarkSurfaceHighlight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

