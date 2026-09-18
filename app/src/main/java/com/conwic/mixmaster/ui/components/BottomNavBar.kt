package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.Charcoal

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Routes.HOME, "Home", Icons.Filled.Home),
    NavItem(Routes.CALCULATOR, "Calculate", Icons.Filled.Science),
    NavItem(Routes.PRODUCTS, "Products", Icons.Filled.Inventory2),
    NavItem(Routes.PROJECTS, "Projects", Icons.Filled.CalendarMonth),
    NavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

private val NavInactive = Color(0xFF8E9095)

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // fillMaxWidth on both: without it the Surface shrinks to the width of its items and the
    // dark bar stops short of the right edge of the screen.
    Surface(color = Charcoal, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                val color = if (selected) Color.White else NavInactive
                Column(
                    modifier = Modifier
                        .background(
                            color = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(imageVector = item.icon, contentDescription = item.label, tint = color, modifier = Modifier.height(20.dp))
                    Text(text = item.label, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}
