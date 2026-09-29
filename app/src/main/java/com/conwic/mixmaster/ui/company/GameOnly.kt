package com.conwic.mixmaster.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.wipe.PhoneWipe
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.breaktime.BreakTimeScreen
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.pagePadding

/**
 * MixMaster on a phone its company took off: Pour Day, and nothing else.
 *
 * The phone has already been emptied (see [com.conwic.mixmaster.data.company.CompanyWipe]); this
 * is what it opens on from then on, every time. No Home, no products, no projects, no settings —
 * nothing that was the company's, and no empty MixMaster to start something new in either. It
 * goes straight into the game. Quitting the game lands on one page: play again, uninstall, or a
 * new access code — the one way back, for somebody the company has taken on again, or given a
 * fresh code for this same phone.
 */
@Composable
fun GameOnlyApp() {
    var playing by rememberSaveable { mutableStateOf(true) }
    if (playing) {
        BreakTimeScreen(onExit = { playing = false }, appName = "Pour Day")
    } else {
        GameOnlyPage(onPlay = { playing = true })
    }
    // the once-only word about what happened, over whichever of the two is showing
    EndedNotice()
}

@Composable
private fun GameOnlyPage(onPlay: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val container = LocalAppContainer.current
    val viewModel: CompanyViewModel = viewModel(
        factory = viewModelFactory { initializer { CompanyViewModel(app, container.userPrefs) } },
    )
    val link by viewModel.link.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val company = remember { CompanyStore.gameOnly(app).orEmpty() }

    // In again: the code worked and the company's data is on the phone. MixMaster comes back,
    // from the start, as on a phone that has just joined.
    val joined = link != null && ui.busy == null
    LaunchedEffect(joined) {
        if (joined) {
            CompanyStore.setGameOnly(app, null)
            CompanyStore.dismissEnded(app)
            PhoneWipe.restart(app)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(text = stringResource(R.string.break_time_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.game_only_body, company),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.break_time_play),
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }

        val problem = ui.problem
        if (problem != null) {
            item {
                CardFlat(edge = MaterialTheme.colorScheme.error) {
                    Text(text = stringResource(problemText(problem)), style = MaterialTheme.typography.bodyMedium)
                    ActionLink(
                        text = stringResource(R.string.co_ok),
                        onClick = viewModel::dismissProblem,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        val busy = ui.busy
        if (busy != null) {
            item {
                CardFlat {
                    Text(text = stringResource(busy), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        }

        item { JoinCard(busy = busy != null, onJoin = viewModel::join, offerBackup = false) }

        item {
            ActionLink(
                text = stringResource(R.string.co_ended_uninstall),
                onClick = { askToUninstall(context) },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
