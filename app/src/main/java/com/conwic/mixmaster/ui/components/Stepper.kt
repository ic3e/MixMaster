package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.ui.theme.StepperShape

/**
 * The design's − / + stepper. The value stays typeable as well as steppable, because real
 * areas are in the thousands of m² and nobody is tapping "+" that many times.
 */
@Composable
fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    minValue: Double = 0.0,
    decimals: Int = 2,
    suffix: String = "",
) {
    fun nudge(delta: Double) {
        val current = value.toDoubleOrNull() ?: 0.0
        onValueChange(formatDecimal((current + delta).coerceAtLeast(minValue), decimals))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, StepperShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, StepperShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(symbol = "−", onClick = { nudge(-step) })
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
        )
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        StepButton(symbol = "+", onClick = { nudge(step) })
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
