package com.conwic.mixmaster.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.domain.PartStock
import com.conwic.mixmaster.domain.bookingsByComponent
import com.conwic.mixmaster.domain.partStock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One product on the shelf, part by part. */
data class WarehouseProduct(
    val product: ProductEntity,
    val parts: List<PartStock>,
) {
    val isShort: Boolean get() = parts.any { it.short > 0.0 }
    val isBooked: Boolean get() = parts.any { it.bookings.isNotEmpty() }
    val hasStock: Boolean get() = parts.any { it.onHand > 0.0 }
}

data class WarehouseUiState(
    val brands: List<String> = emptyList(),
    val brandFilter: String = "All",
    val products: List<WarehouseProduct> = emptyList(),
) {
    /** Everything that has to be ordered, whatever product it belongs to. */
    val toOrder: List<Pair<WarehouseProduct, PartStock>>
        get() = products.flatMap { item -> item.parts.filter { it.short > 0.0 }.map { item to it } }
}

class WarehouseViewModel(
    private val productRepository: ProductRepository,
    private val projectRepository: ProjectRepository,
    private val stockRepository: StockRepository,
) : ViewModel() {

    private val brandFilter = kotlinx.coroutines.flow.MutableStateFlow("All")

    private val shelf = combine(
        productRepository.observeAll(),
        productRepository.observeAllComponents(),
        stockRepository.observeAll(),
        projectRepository.observeAll(),
        projectRepository.observeAllRooms(),
    ) { products, components, stock, projects, rooms ->
        val componentsByProduct = components.groupBy { it.productId }
        val productsById = products.associate { product ->
            product.id to ProductWithComponents(product, componentsByProduct[product.id].orEmpty())
        }
        val bookings = bookingsByComponent(
            projects = projects,
            roomsByProject = rooms.groupBy { it.projectId },
            productsById = productsById,
        )
        val stockByComponent = stock.associateBy { it.componentId }
        products.map { product ->
            WarehouseProduct(
                product = product,
                parts = componentsByProduct[product.id].orEmpty().map { component ->
                    partStock(
                        component = component,
                        stock = stockByComponent[component.id],
                        bookings = bookings[component.id].orEmpty(),
                    )
                },
            )
        }
    }

    val uiState: StateFlow<WarehouseUiState> = combine(shelf, brandFilter) { items, brand ->
        WarehouseUiState(
            brands = listOf("All") + items.map { it.product.brand }.distinct().sorted(),
            brandFilter = brand,
            products = items.filter { brand == "All" || it.product.brand == brand },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WarehouseUiState())

    fun setBrandFilter(brand: String) {
        brandFilter.value = brand
    }

    fun setStock(productId: Long, componentId: Long, fullPacks: Int, openAmount: Double) {
        viewModelScope.launch { stockRepository.set(productId, componentId, fullPacks, openAmount) }
    }
}
