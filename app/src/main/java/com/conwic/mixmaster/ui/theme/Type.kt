package com.conwic.mixmaster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The design prototype uses Archivo (display/headings) and Manrope (body) from Google Fonts.
 * Drop the matching .ttf files under res/font/ and swap FontFamily.Default below for a
 * downloadable/bundled FontFamily to match the web prototype exactly — system sans-serif
 * is used here as a safe default since font binaries can't be fetched in this environment.
 */
val DisplayFontFamily = FontFamily.Default
val BodyFontFamily = FontFamily.Default

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
