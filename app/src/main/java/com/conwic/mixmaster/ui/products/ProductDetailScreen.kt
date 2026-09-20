package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.openableUrl
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.navigation.navigateToTopLevel
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.GhostButton
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

@Composable
fun ProductDetailScreen(navController: NavHostController, productId: Long) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val viewModel: ProductDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ProductDetailViewModel(container.productRepository, productId) } },
    )
    val productWithComponents by viewModel.productWithComponents.collectAsState()
    val usageLogs by viewModel.usageLogs.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)
    val data = productWithComponents ?: return
    val product = data.product
    val siteAverage = if (usageLogs.isEmpty()) null else usageLogs.map { it.doseGramsPerM2 }.average()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MixMasterTopBar(
                title = product.name,
                onBack = { navController.popBackStack() },
                actions = {
                    if (role == Role.EMPLOYER) {
                        IconButton(onClick = { navController.navigate(Routes.productEdit(product.id)) }) {
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "${product.brand} · ${product.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(text = stringResource(R.string.pd_mix_ratio), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        RatioBadge(text = product.ratioLabel, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = stringResource(R.string.pd_coverage), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${product.typicalDoseGramsPerM2.toInt()}", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "g/m² ${product.doseUnitLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (product.sourceNote.isNotBlank()) {
                    Text(text = product.sourceNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
                // Only offered when it's something the system can actually open — the field is
                    // free text, and handing "test datasheet" to the browser took the app down.
                    val datasheetLink = openableUrl(product.datasheetUrl)
                    if (datasheetLink != null) {
                    Text(
                        text = stringResource(R.string.calc_view_datasheet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .tappableText { runCatching { uriHandler.openUri(datasheetLink) } },
                    )
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.pd_field_data))
                CardFlat {
                    FieldDataRangeBar(
                        min = product.minDoseGramsPerM2,
                        max = product.maxDoseGramsPerM2,
                        typical = product.typicalDoseGramsPerM2,
                        siteAverage = siteAverage,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${product.minDoseGramsPerM2.toInt()} g/m²", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${product.maxDoseGramsPerM2.toInt()} g/m²", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.pd_datasheet_typical), style = MaterialTheme.typography.bodyMedium)
                        Text(text = "${product.typicalDoseGramsPerM2.toInt()} g/m²", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.pd_site_average), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (siteAverage != null) stringResource(R.string.pd_site_average_value, siteAverage.toInt().toString(), usageLogs.size) else stringResource(R.string.calc_no_logged),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (siteAverage != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.pd_components)) }
        items(data.components) { component ->
            ExpandableComponentCard(component)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (role == Role.EMPLOYER) {
                    GhostButton(
                        text = stringResource(R.string.pd_edit_product),
                        onClick = { navController.navigate(Routes.productEdit(product.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.pd_use_in_calculator),
                    onClick = {
                        // Says which product before going there; the button used to open the
                        // calculator on whatever happened to be selected already.
                        scope.launch { container.userPrefs.setLastProductId(product.id) }
                        navController.navigateToTopLevel(Routes.CALCULATOR)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpandableComponentCard(component: ProductComponentEntity) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetails = component.density.isNotBlank() || component.potLife.isNotBlank() || component.notes.isNotBlank()
    CardFlat(modifier = Modifier.fillMaxWidth().clip(CardShape).clickable(enabled = hasDetails) { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = component.label, style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.pd_parts_line, component.basis, component.ratioParts.toInt().toString()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (component.packageSize > 0.0) {
                        stringResource(R.string.pd_sold_in, formatDecimal(component.packageSize, 2), component.packageUnit)
                    } else {
                        stringResource(R.string.pd_no_pack_size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (component.packageSize > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded && hasDetails) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                if (component.density.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.pd_density), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = component.density, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (component.potLife.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = stringResource(R.string.product_pot_life), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = component.potLife, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (component.notes.isNotBlank()) {
                    Text(text = component.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** A track spanning [min]..[max] with a marker for the datasheet [typical] and, if present, a
 * second marker for the real logged [siteAverage] — the design's "field data" concept made
 * real instead of static. */
@Composable
private fun FieldDataRangeBar(min: Double, max: Double, typical: Double, siteAverage: Double?) {
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(trackColor, RoundedCornerShape(999.dp)))
        Box(modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp)))
        val typicalFraction = ((typical - min) / span).coerceIn(0.0, 1.0).toFloat()
        Box(
            modifier = Modifier
                .offset(x = maxWidth * typicalFraction - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(accentColor),
        )
        if (siteAverage != null) {
            val avgFraction = ((siteAverage - min) / span).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * avgFraction - 2.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(secondaryColor, RoundedCornerShape(999.dp)),
            )
        }
    }
}
