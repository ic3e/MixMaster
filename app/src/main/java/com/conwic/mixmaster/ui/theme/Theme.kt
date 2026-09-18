package com.conwic.mixmaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    secondary = Accent2,
    onSecondary = Surface,
    background = BgCream,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = BgSoft,
    onSurfaceVariant = TextDim,
    outline = Border,
    outlineVariant = BorderSoft,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Accent2,
    onPrimary = Charcoal,
    secondary = Accent,
    onSecondary = Charcoal,
    background = Color(0xFF1B1917),
    onBackground = Color(0xFFEDE7DC),
    surface = Color(0xFF242220),
    onSurface = Color(0xFFEDE7DC),
    surfaceVariant = Color(0xFF2E2B27),
    onSurfaceVariant = Color(0xFFB9B2A6),
    outline = Color(0xFF454138),
    error = Danger,
)

@Composable
fun MixMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MixMasterTypography,
        shapes = MixMasterShapes,
        content = content,
    )
}
