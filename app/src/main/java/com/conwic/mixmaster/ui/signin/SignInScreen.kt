package com.conwic.mixmaster.ui.signin

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.theme.CardShape
import kotlinx.coroutines.launch
import com.conwic.mixmaster.ui.components.PrimaryButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.ui.theme.DisplayFontFamily

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
            // The badge is a vector and the wordmark is type, so both stay crisp and both follow
            // the theme — the flat charcoal lockup went invisible on a dark background.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.conwic_badge),
                    contentDescription = "ConWiC",
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = "CONWIC",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        item {
            Column {
                Text(text = "Who's signing in?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "MixMaster works differently depending on your role — you can switch anytime from Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            RoleCard(
                title = "Employer / Office",
                subtitle = "Set up projects, products & pricing",
                bullets = listOf(
                    "Create and edit projects, clients & the product library",
                    "Assign tasks and rooms to the crew",
                    "Generate reports and manage the team",
                ),
                selected = selectedRole == Role.EMPLOYER,
                onClick = { selectedRole = Role.EMPLOYER },
            )
        }
        item {
            RoleCard(
                title = "Worker / Field crew",
                subtitle = "Use what the office has set up",
                bullets = listOf(
                    "Run the calculator with the office's product list",
                    "Check off tasks and rooms as they're done",
                    "Add site photos and notes — can't edit setup data",
                ),
                selected = selectedRole == Role.WORKER,
                onClick = { selectedRole = Role.WORKER },
            )
        }
        item {
            PrimaryButton(
                text = "Continue as ${selectedRole.name.lowercase()}",
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
