package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.ui.theme.TextFaint

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = TextFaint,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
