package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.CardShape

/** The flat, warm-surface card used throughout the design for grouped rows of info. */
@Composable
fun CardFlat(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CardShape)
            .padding(16.dp),
        content = content,
    )
}

/** The solid accent-colored "hero" card (quick actions, rollup stats). */
@Composable
fun CardAccent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, CardShape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    CardFlat(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = valueColor, fontWeight = FontWeight.ExtraBold)
    }
}
