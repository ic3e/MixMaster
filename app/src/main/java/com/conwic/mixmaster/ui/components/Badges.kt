package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.ui.theme.BadgeShape
import com.conwic.mixmaster.ui.theme.ChipShape
import com.conwic.mixmaster.ui.theme.DisplayFontFamily
import com.conwic.mixmaster.ui.theme.PillBrandBg
import com.conwic.mixmaster.ui.theme.PillBrandText

/** The mix-ratio badge, e.g. "100 : 35 wt" or "2K". */
@Composable
fun RatioBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, BadgeShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, BadgeShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The dark manufacturer pill that leads a product row, e.g. "IDEAL WORK". */
@Composable
fun BrandPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = PillBrandText,
        modifier = modifier
            .background(PillBrandBg, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
