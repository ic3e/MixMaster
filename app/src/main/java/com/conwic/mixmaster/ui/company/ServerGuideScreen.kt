package com.conwic.mixmaster.ui.company

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.ServerFiles
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SegmentedTabs
import com.conwic.mixmaster.ui.components.pagePadding

/**
 * How to put up the company's server, in the app itself and in plain words.
 *
 * Written for the person who will be doing it after the app has been handed over — an employer
 * who has never seen a script — so it is here rather than in a document somewhere, it carries the
 * server's files with it, and it says ahead of time which screens look alarming and why they
 * are fine.
 */
@Composable
fun ServerGuideScreen(navController: NavHostController) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val subject = stringResource(R.string.co_guide_send_subject)
    val chooser = stringResource(R.string.co_guide_chooser)

    val googleSteps = listOf(
        R.string.co_g1, R.string.co_g2, R.string.co_g3, R.string.co_g4,
        // The access setting is a step of its own: it sits half way down Google's form, and missing
        // it is the one mistake that leaves the app shut out of a server that is otherwise right.
        R.string.co_g5, R.string.co_g6, R.string.co_g6b, R.string.co_g7, R.string.co_g8,
    )
    val websiteSteps = listOf(
        R.string.co_w1, R.string.co_w2, R.string.co_w3, R.string.co_w4, R.string.co_w5, R.string.co_w6,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MixMasterTopBar(title = stringResource(R.string.co_guide_title), onBack = { navController.popBackStack() }) }
        item {
            Text(
                text = stringResource(R.string.co_guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SegmentedTabs(
                titles = listOf(stringResource(R.string.co_guide_google), stringResource(R.string.co_guide_website)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }
        val steps = if (tab == 0) googleSteps else websiteSteps
        item {
            CardFlat {
                steps.forEachIndexed { index, step ->
                    StepRow(number = index + 1, text = stringResource(step))
                    // The file goes to the computer before anything else can happen there.
                    if (index == 0) {
                        PrimaryButton(
                            text = stringResource(if (tab == 0) R.string.co_guide_send_google else R.string.co_guide_send_website),
                            onClick = {
                                if (tab == 0) {
                                    ServerFiles.share(context, ServerFiles.googleScript(context), "text/plain", subject, chooser)
                                } else {
                                    ServerFiles.share(context, ServerFiles.websiteZip(context), "application/zip", subject, chooser)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(if (tab == 0) R.string.co_g_note else R.string.co_w_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
