package com.conwic.mixmaster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.R

/**
 * One typeface, Manrope, for everything from the page title to the smallest label.
 *
 * The design paired it with Archivo for headings and buttons, and on the phone the two sat side by
 * side on every card — a title in one, the line under it in the other, a chip and the button next
 * to it in different letters — which read as a mistake rather than as a choice. Headings stand out
 * by weight and size now, not by a second face.
 */
val AppFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/**
 * Every letter drawn on its own. Manrope joins "fl" and "fi" into one shape, the way a printed
 * book does, and on a phone "floors" read as a slip — the f's bar run into the l as if the two
 * had been stuck together. Written the way the text engine itself switches them off.
 */
const val PlainLetters = "-liga,-clig"

/**
 * All fifteen styles are set here, including ones the app's own code never names. One left out is
 * not an error — Material quietly fills it with its own, in Roboto — which is how the mixing
 * screen's title and its start button came out in a different typeface, and the date and time
 * pickers, which use styles the app does not, would have done the same.
 */
val MixMasterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    // The date and time pickers draw their big figures in the display and headline styles.
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontFeatureSettings = PlainLetters,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
    ),
)
