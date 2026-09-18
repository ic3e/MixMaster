package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes

@Composable
fun ProductDetailScreen(navController: NavHostController, productId: Long) {
    val container = LocalAppContainer.current
    val viewModel: ProductDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ProductDetailViewModel(container.productRepository, productId) } },
    )
    val productWithComponents by viewModel.productWithComponents.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)
    val data = productWithComponents ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MixMasterTopBar(
                title = data.product.name,
                onBack = { navController.popBackStack() },
                actions = {
                    if (role == Role.EMPLOYER) {
                        IconButton(onClick = { navController.navigate(Routes.productEdit(data.product.id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.delete { navController.popBackStack() } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        }
        item {
            CardFlat {
                Text(text = "${data.product.brand} · ${data.product.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(text = data.product.rangeNote, style = MaterialTheme.typography.bodyMedium)
                if (data.product.sourceNote.isNotBlank()) {
                    Text(text = data.product.sourceNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionLabel(text = "Mix ratio")
        }
        item {
            CardFlat {
                data.components.forEach { component ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = component.label, style = MaterialTheme.typography.bodyMedium)
                        Text(text = component.ratioParts.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
