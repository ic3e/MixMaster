package com.conwic.mixmaster.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import kotlinx.coroutines.launch
import com.conwic.mixmaster.ui.components.PrimaryButton
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.conwic.mixmaster.R

// Resource ids: this list is built at class load, before any language is known.
private data class OnboardingStep(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val steps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Science,
        title = R.string.onboarding_1_title,
        body = R.string.onboarding_1_body,
    ),
    OnboardingStep(
        icon = Icons.Filled.Inventory2,
        title = R.string.onboarding_2_title,
        body = R.string.onboarding_2_body,
    ),
    OnboardingStep(
        icon = Icons.Filled.CalendarMonth,
        title = R.string.onboarding_3_title,
        body = R.string.onboarding_3_body,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineLarge)
        }
        items(steps) { step ->
            CardFlat {
                Icon(imageVector = step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Text(text = stringResource(step.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.onboarding_start),
                onClick = {
                    scope.launch {
                        container.userPrefs.setOnboardingSeen(true)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
