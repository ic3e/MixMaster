package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
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
import com.conwic.mixmaster.data.db.entity.familyId
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.domain.formatDecimal
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.conwic.mixmaster.ui.components.SegmentedTabs
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.ProductIdentity
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
        factory = viewModelFactory {
            initializer { ProductsViewModel(container.productRepository, container.solutionRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)
    val solutions by viewModel.solutions.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        floatingActionButton = {
            if (role == Role.EMPLOYER) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(if (tab == 0) Routes.PRODUCT_ADD else Routes.SOLUTION_ADD)
                    },
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
            contentPadding = pagePadding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(text = stringResource(R.string.products_title), style = MaterialTheme.typography.headlineMedium) }

            // Two lists, because they are two things: what you buy, and what you make of it.
            item {
                SegmentedTabs(
                    titles = listOf(
                        stringResource(R.string.products_tab_products),
                        stringResource(R.string.products_tab_solutions),
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

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
                // Above the filters and shared by both tabs: what you are looking for is a name
                // you half remember, not a brand and a category you can pick off two lists.
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    // A placeholder rather than a label: there is nothing to name here, and a
                    // word floating on the border is what made the fields look stickered.
                    placeholder = { Text(stringResource(R.string.products_search)) },
                    singleLine = true,
                    trailingIcon = {
                        if (state.search.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (tab == 0) {
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
                    ProductIdentity(
                        name = product.name,
                        brand = product.brand,
                        detail = if (product.packageSize > 0.0) {
                            "${formatDecimal(product.packageSize, 2)} ${product.packageUnit} ${product.packageType}"
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.products_solutions_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // One row per mix: its coats live inside it, and listing them here would read
                // as three products where the shed has one.
                val needle = state.search.trim()
                val mixes = solutions
                    .filter { it.parentId == 0L }
                    .filter { needle.isBlank() || it.matches(needle) }
                items(mixes) { solution ->
                    CardFlat(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .clickable { navController.navigate(Routes.solutionEdit(solution.id)) },
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val coats = solutions.count { it.familyId == solution.familyId }
                            ProductIdentity(
                                name = solution.name,
                                brand = solution.brand,
                                detail = if (coats > 1) stringResource(R.string.solution_coat_count, coats) else null,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            if (solution.ratioLabel.isNotBlank()) {
                                RatioBadge(text = solution.ratioLabel, modifier = Modifier.align(Alignment.Top))
                            }
                        }
                    }
                }
            }
        }
    }
}
