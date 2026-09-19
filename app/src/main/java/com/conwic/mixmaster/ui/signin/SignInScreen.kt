package com.conwic.mixmaster.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ConwicLockup
import com.conwic.mixmaster.ui.theme.CardShape
import kotlinx.coroutines.launch
import com.conwic.mixmaster.ui.components.PrimaryButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource

@Composable
fun SignInScreen(onContinue: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var selectedRole by remember { mutableStateOf(Role.EMPLOYER) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ConwicLockup(height = 34.dp, modifier = Modifier.padding(bottom = 4.dp))
        }
        item {
            Column {
                Text(text = stringResource(R.string.signin_who), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.signin_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            RoleCard(
                title = stringResource(R.string.signin_employer),
                subtitle = stringResource(R.string.signin_employer_sub),
                bullets = listOf(
                    stringResource(R.string.signin_employer_b1),
                    stringResource(R.string.signin_employer_b2),
                    stringResource(R.string.signin_employer_b3),
                ),
                selected = selectedRole == Role.EMPLOYER,
                onClick = { selectedRole = Role.EMPLOYER },
            )
        }
        item {
            RoleCard(
                title = stringResource(R.string.signin_worker),
                subtitle = stringResource(R.string.signin_worker_sub),
                bullets = listOf(
                    stringResource(R.string.signin_worker_b1),
                    stringResource(R.string.signin_worker_b2),
                    stringResource(R.string.signin_worker_b3),
                ),
                selected = selectedRole == Role.WORKER,
                onClick = { selectedRole = Role.WORKER },
            )
        }
        item {
            PrimaryButton(
                text = stringResource(R.string.signin_continue, stringResource(if (selectedRole == Role.EMPLOYER) R.string.role_employer else R.string.role_worker).lowercase()),
                onClick = {
                    scope.launch {
                        container.userPrefs.setRole(selectedRole)
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    bullets: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor, CardShape)
            .border(1.dp, borderColor, CardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioDot(selected = selected)
        }
        Column(modifier = Modifier.padding(top = 12.dp)) {
            bullets.forEach { bullet ->
                Text(text = "•  $bullet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(4.dp)
            .size(22.dp)
            .background(if (selected) color else MaterialTheme.colorScheme.surface, CircleShape)
            .border(2.dp, color, CircleShape),
    )
}
