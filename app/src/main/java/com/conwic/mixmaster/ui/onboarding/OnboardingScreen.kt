package com.conwic.mixmaster.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

private data class OnboardingStep(val icon: ImageVector, val title: String, val body: String)

private val steps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Science,
        title = "Never guess a mix again",
        body = "Pick a product, enter the m² (or thickness), and MixMaster works out exactly how much of each part you need — no more eyeballing bag-to-bucket ratios on site.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Inventory2,
        title = "Your whole product library, in one place",
        body = "Ideal Work, Mapei, Sika, Ardex and anything else you add — with real ratios and coverage from the datasheets, searchable by brand and category.",
    ),
    OnboardingStep(
        icon = Icons.Filled.CalendarMonth,
        title = "Projects, rooms, tasks & the calendar",
        body = "Break a project into floors and rooms, assign a product per room, track tasks against a due date, and everything shows up on one shared calendar.",
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
            Text(text = "Welcome to MixMaster", style = MaterialTheme.typography.headlineLarge)
        }
        items(steps) { step ->
            CardFlat {
                Icon(imageVector = step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Text(text = step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            PrimaryButton(
                text = "Get started",
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
