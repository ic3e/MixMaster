package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.BrandPill
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes

/**
 * A bought item: what it is, how it is sold, and which recipes call for it.
 *
 * No coverage and no ratio — those belong to the solution the product goes into, not to a bag
 * of powder. What matters here is the pack, because that is what gets ordered and counted.
 */
@Composable
fun ProductDetailScreen(navController: NavHostController, productId: Long) {
    val container = LocalAppContainer.current
    val viewModel: ProductDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProductDetailViewModel(container.productRepository, container.solutionRepository, productId)
            }
        },
    )
    val product by viewModel.product.collectAsState()
    val usedIn by viewModel.usedIn.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)
    val current = product ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MixMasterTopBar(
                title = current.name,
                onBack = { navController.popBackStack() },
                actions = {
                    if (role == Role.EMPLOYER) {
                        IconButton(onClick = { navController.navigate(Routes.productEdit(current.id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { viewModel.delete { navController.popBackStack() } }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        }

        item {
            CardFlat {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (current.brand.isNotBlank()) BrandPill(text = current.brand)
                    if (current.category.isNotBlank()) {
                        Text(
                            text = current.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (current.brand.isNotBlank()) 10.dp else 0.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(R.string.pd_sold_as), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (current.packageSize > 0.0) {
                            "${formatDecimal(current.packageSize, 2)} ${current.packageUnit} ${current.packageType}"
                        } else {
                            stringResource(R.string.wh_no_pack_size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(R.string.pd_density), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (current.densityKgPerL > 0.0) {
                            "${formatDecimal(current.densityKgPerL, 3)} kg/L"
                        } else {
                            stringResource(R.string.pd_density_unknown)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.pd_used_in)) }

        if (usedIn.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.pd_used_in_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(usedIn.size) { index ->
            val solution = usedIn[index]
            CardFlat {
                Text(
                    text = if (solution.brand.isBlank()) solution.name else "${solution.brand} — ${solution.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
