package com.conwic.mixmaster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.R

/** Archivo (display/headings) and Manrope (body) — the same Google Fonts pair the design prototype uses. */
val DisplayFontFamily = FontFamily(
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
    Font(R.font.archivo_black, FontWeight.Black),
)
val BodyFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val MixMasterTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
    ),
)
