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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.ui.theme.StepperShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.domain.toNumberOrNull

/**
 * The design's − / + stepper. The value stays typeable as well as steppable, because real
 * areas are in the thousands of m² and nobody is tapping "+" that many times.
 *
 * The caret lives here rather than being derived from [value]: the screen's state round-trips
 * through a StateFlow and so comes back a frame later, which would otherwise reset the caret
 * to the start on every keystroke and type "12" as "21".
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
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    fun nudge(delta: Double) {
        val current = field.text.toNumberOrNull() ?: 0.0
        val next = formatDecimal((current + delta).coerceAtLeast(minValue), decimals)
        field = TextFieldValue(next, TextRange(next.length))
        onValueChange(next)
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
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
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
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
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
