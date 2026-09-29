package com.conwic.mixmaster.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.company.rememberAccess
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.pagePadding
import kotlinx.coroutines.launch

// Resource ids: this list is built at class load, before any language is known.
private data class OnboardingStep(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/** The work, in the order a job goes: the mix, what it is made of, the job, the shed, the site. */
private val work = listOf(
    OnboardingStep(Icons.Filled.Science, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingStep(Icons.Filled.Inventory2, R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingStep(Icons.Filled.CalendarMonth, R.string.onboarding_3_title, R.string.onboarding_3_body),
    OnboardingStep(Icons.Filled.Warehouse, R.string.onboarding_4_title, R.string.onboarding_4_body),
    OnboardingStep(Icons.Filled.PhotoCamera, R.string.onboarding_5_title, R.string.onboarding_5_body),
)

/**
 * What MixMaster does, a card a thing — after sign-in, and again from Settings → Help.
 *
 * The company card is the one that changes with the phone: on its own, it says how to share with a
 * crew; on the owner's, how people are added and taken off; on a worker's, whose figures these
 * are and who decides what they may change.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val access = rememberAccess()
    val link by remember { CompanyStore.link(context.applicationContext) }.collectAsState()
    val company = link?.companyName
    // the same phones that see Settings → Demo
    val demoOffered = access.catalogue && access.projects && access.warehouse && (access.owner || !access.inCompany)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(text = stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineLarge)
                if (demoOffered) {
                    Text(
                        text = stringResource(R.string.onboarding_demo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        items(work) { step ->
            StepCard(icon = step.icon, title = stringResource(step.title), body = stringResource(step.body))
        }
        item {
            StepCard(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.onboarding_6_title),
                body = when {
                    company == null -> stringResource(R.string.onboarding_6_solo)
                    access.owner -> stringResource(R.string.onboarding_6_owner, company)
                    else -> stringResource(R.string.onboarding_6_worker, company)
                },
            )
        }
        item {
            StepCard(
                icon = Icons.Rounded.SportsEsports,
                title = stringResource(R.string.onboarding_7_title),
                body = stringResource(R.string.onboarding_7_body),
            )
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

@Composable
private fun StepCard(icon: ImageVector, title: String, body: String) {
    CardFlat {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
