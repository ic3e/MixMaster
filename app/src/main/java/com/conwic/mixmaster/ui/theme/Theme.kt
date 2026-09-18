package com.conwic.mixmaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every slot is filled deliberately: any left unset falls back to Material's default purple
// baseline, which is how stray lavender ends up on sliders, badges and floating buttons.
private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    inversePrimary = Accent2,
    secondary = Accent2,
    onSecondary = Color.White,
    secondaryContainer = Accent2Dim,
    onSecondaryContainer = PillPlanningText,
    tertiary = Ok,
    onTertiary = Color.White,
    tertiaryContainer = OkDim,
    onTertiaryContainer = Ok,
    background = BgCream,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = BgSoft,
    onSurfaceVariant = TextDim,
    surfaceTint = Accent,
    inverseSurface = Charcoal,
    inverseOnSurface = TextOnDark,
    outline = Border,
    outlineVariant = BorderSoft,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerDim,
    onErrorContainer = Danger,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Accent2,
    onPrimary = CharcoalDeep,
    primaryContainer = Color(0xFF3B332B),
    onPrimaryContainer = Accent2,
    inversePrimary = Accent,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A302A),
    onSecondaryContainer = Accent2,
    tertiary = Ok,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B352D),
    onTertiaryContainer = Color(0xFF9CC3A2),
    background = Color(0xFF1B1917),
    onBackground = Color(0xFFEDE7DC),
    surface = Color(0xFF242220),
    onSurface = Color(0xFFEDE7DC),
    surfaceVariant = Color(0xFF2E2B27),
    onSurfaceVariant = Color(0xFFB9B2A6),
    surfaceTint = Accent2,
    inverseSurface = Color(0xFFEDE7DC),
    inverseOnSurface = Color(0xFF1B1917),
    outline = Color(0xFF454138),
    outlineVariant = Color(0xFF383430),
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFF3B2523),
    onErrorContainer = Color(0xFFE0A19C),
    scrim = Color.Black,
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
