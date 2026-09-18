package com.conwic.mixmaster.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.formatWeek
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.StatCard
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.navigation.navigateToTopLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.projectRepository, container.productRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val now = remember(state) { LocalDateTime.now() }
    val today = now.toLocalDate()
    val dayPart = dayPartFor(now.toLocalTime())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.conwic_badge),
                        contentDescription = "ConWiC",
                        modifier = Modifier.size(38.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = greetingFor(dayPart), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = quipFor(today, dayPart),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "Projects", value = "${state.activeProjectCount} active")
                StatCard(modifier = Modifier.weight(1f), label = "Tasks today", value = "${state.todayTasks.size}", valueColor = MaterialTheme.colorScheme.secondary)
                StatCard(modifier = Modifier.weight(1f), label = "Products", value = "${state.productCount}")
            }
        }

        item {
            CardAccent(modifier = Modifier.clickable { navController.navigateToTopLevel(Routes.CALCULATOR) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Quick calculate", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            text = "Get an exact mix split in seconds",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = "Today")
                if (state.todayTasks.isEmpty()) {
                    CardFlat { Text(text = "Nothing due today.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    CardFlat {
                        state.todayTasks.forEachIndexed { index, task ->
                            TaskRow(task = task, onToggle = { viewModel.toggleTask(task) })
                            if (index != state.todayTasks.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(text = "This week · ${formatWeek(today)}")
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { navController.navigate(Routes.CALENDAR) },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    state.week.forEach { day -> WeekDayCell(day) }
                }
            }
        }

        if (state.recentProducts.isNotEmpty()) {
            item {
                Column {
                    SectionLabel(text = "Recently added products")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentProducts.forEach { product ->
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                                    .clickable { navController.navigate(Routes.productDetail(product.id)) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: HomeTaskUi, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(text = task.projectName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(9.dp)
                .background(Color(android.graphics.Color.parseColor(task.dotColorHex)), CircleShape),
        )
    }
}

@Composable
private fun WeekDayCell(day: WeekDayUi) {
    val bg = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (day.isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = day.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(36.dp)
                .background(bg, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, color = fg)
        }
    }
}
