package com.conwic.mixmaster.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.material.icons.filled.Warehouse
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

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.Charcoal
import androidx.compose.ui.draw.clip

// The label is a resource id rather than text: this list is built once at class load,
// long before any language is known.
private data class NavItem(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    NavItem(Routes.WAREHOUSE, R.string.nav_warehouse, Icons.Filled.Warehouse),
    NavItem(Routes.PRODUCTS, R.string.nav_products, Icons.Filled.Inventory2),
    NavItem(Routes.PROJECTS, R.string.nav_projects, Icons.Filled.CalendarMonth),
    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

private val NavInactive = Color(0xFF8E9095)

private val NavBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** The same shade the see-through white used to give on the charcoal, made solid. */
private val NavSelected = Color.White.copy(alpha = 0.12f).compositeOver(Charcoal)

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // fillMaxWidth on both: without it the Surface shrinks to the width of its items and the
    // dark bar stops short of the right edge of the screen.
    // Flat, like the design: the charcoal bar reads as its own layer without a drop shadow.
    // The top corners are rounded like a sheet's. Square, the dark block cut straight across the
    // page and looked like the bottom of the screen had been painted over. A fifth see-through,
    // so the page can be seen carrying on underneath it — the pages leave room to scroll clear.
    Surface(color = Charcoal.copy(alpha = 0.8f), shape = NavBarShape, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                val color = if (selected) Color.White else NavInactive
                // An equal fifth each, the lit pill filling it: sized to its word, "Home" had a
                // small pill and "Warehouse" a wide one, and it jumped as you moved between tabs.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        // Solid: the bar under it lets the page through, and a lit-up tab with a card
                        // showing through it read as part of the page rather than as the tab you are on.
                        .background(color = if (selected) NavSelected else Color.Transparent)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val label = stringResource(item.labelRes)
                    Icon(imageVector = item.icon, contentDescription = label, tint = color, modifier = Modifier.height(20.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
