package com.conwic.mixmaster.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.theme.CardShape
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
    /** The guide's chapter that shows it being done. */
    val chapter: Int,
)

/** The work, in the order a job goes: the mix, what it is made of, the job, the shed, the site. */
private val work = listOf(
    OnboardingStep(Icons.Filled.Science, R.string.onboarding_1_title, R.string.onboarding_1_body, chapter = 1),
    OnboardingStep(Icons.Filled.Inventory2, R.string.onboarding_2_title, R.string.onboarding_2_body, chapter = 2),
    OnboardingStep(Icons.Filled.CalendarMonth, R.string.onboarding_3_title, R.string.onboarding_3_body, chapter = 3),
    OnboardingStep(Icons.Filled.Warehouse, R.string.onboarding_4_title, R.string.onboarding_4_body, chapter = 4),
    OnboardingStep(Icons.Filled.PhotoCamera, R.string.onboarding_5_title, R.string.onboarding_5_body, chapter = 5),
)

/**
 * What MixMaster does, a card a thing — from Settings → Help, and on a phone that joined a company
 * before it had seen anything. The guide shows the same things being done: the whole of it from
 * the card at the top, a chapter from each card's "Watch how".
 *
 * The company card is the one that changes with the phone: on its own, it says how to share with a
 * crew; on the owner's, how people are added and taken off; on a worker's, whose figures these
 * are and who decides what they may change.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onWatch: (chapter: Int) -> Unit) {
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
        item {
            CardFlat(modifier = Modifier.clip(CardShape).clickable { onWatch(0) }, edge = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = stringResource(R.string.guide_watch), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(R.string.guide_watch_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(work) { step ->
            StepCard(icon = step.icon, title = stringResource(step.title), body = stringResource(step.body), onWatch = { onWatch(step.chapter) })
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
                onWatch = { onWatch(6) },
            )
        }
        item {
            StepCard(
                icon = Icons.Rounded.SportsEsports,
                title = stringResource(R.string.onboarding_7_title),
                body = stringResource(R.string.onboarding_7_body),
                onWatch = { onWatch(7) },
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
private fun StepCard(icon: ImageVector, title: String, body: String, onWatch: () -> Unit) {
    CardFlat {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ActionLink(text = stringResource(R.string.guide_watch_part), onClick = onWatch, modifier = Modifier.padding(top = 8.dp))
    }
}
