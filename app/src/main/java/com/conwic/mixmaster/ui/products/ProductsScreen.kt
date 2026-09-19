package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.navigation.Routes
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.ui.theme.CardShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

@Composable
fun ProductsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: ProductsViewModel = viewModel(
        factory = viewModelFactory { initializer { ProductsViewModel(container.productRepository) } },
    )
    val state by viewModel.uiState.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)

    Scaffold(
        floatingActionButton = {
            if (role == Role.EMPLOYER) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.PRODUCT_ADD) },
                    // Flat like the rest of the design — the default FAB shadow reads as a smudge here.
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.products_add))
                }
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            // Extra room at the bottom so the floating + doesn't sit on top of the last card.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(text = stringResource(R.string.products_title), style = MaterialTheme.typography.headlineMedium) }

            if (role == Role.WORKER) {
                item {
                    CardFlat {
                        Text(
                            text = stringResource(R.string.products_worker_note),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                // Dropdowns rather than chip rows: the brand and category lists grow with the
                // catalogue, and a scrolling row of chips hides whatever is off the right edge.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DropdownField(
                        label = stringResource(R.string.filter_brand),
                        selected = state.brandFilter,
                        options = state.brands,
                        onSelect = viewModel::setBrandFilter,
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = stringResource(R.string.filter_type),
                        selected = state.categoryFilter,
                        options = state.categories,
                        onSelect = viewModel::setCategoryFilter,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text(
                    text = pluralStringResource(
                        R.plurals.products_count,
                        state.visibleProducts.size,
                        state.visibleProducts.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(state.visibleProducts) { product ->
                CardFlat(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { navController.navigate(Routes.productDetail(product.id)) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = product.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = product.rangeNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (product.ratioLabel.isNotBlank()) {
                            RatioBadge(text = product.ratioLabel, modifier = Modifier.align(Alignment.Top))
                        }
                    }
                }
            }
        }
    }
}
