package com.conwic.mixmaster.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.bookingsByProduct
import com.conwic.mixmaster.domain.productStock
import com.conwic.mixmaster.domain.solutionMix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WarehouseUiState(
    val brands: List<String> = emptyList(),
    val brandFilter: String = "All",
    val items: List<ProductStock> = emptyList(),
) {
    /** Everything that has to be ordered, whatever it belongs to. */
    val toOrder: List<ProductStock> get() = items.filter { it.short > 0.0 }
}

class WarehouseViewModel(
    private val productRepository: ProductRepository,
    private val projectRepository: ProjectRepository,
    private val solutionRepository: SolutionRepository,
    private val stockRepository: StockRepository,
) : ViewModel() {

    private val brandFilter = MutableStateFlow("All")

    /** What every unfinished job has spoken for, worked out from its rooms each time. */
    private val bookings = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllRooms(),
        projectRepository.observeAllLayers(),
        solutionRepository.observeAllWithLines(),
        productRepository.observeAll(),
    ) { projects, rooms, layers, solutions, products ->
        val productsById = products.associateBy { it.id }
        val mixes = solutions.associate { it.solution.id to solutionMix(it.solution, it.lines, productsById) }
        bookingsByProduct(projects, rooms, layers.groupBy { it.roomId }, mixes, productsById)
    }

    private val shelf = combine(
        productRepository.observeAll(),
        stockRepository.observeAll(),
        bookings,
    ) { products, stock, booked ->
        val stockByProduct = stock.associateBy { it.productId }
        products.map { product ->
            productStock(product, stockByProduct[product.id], booked[product.id].orEmpty())
        }
    }

    val uiState: StateFlow<WarehouseUiState> = combine(shelf, brandFilter) { items, brand ->
        WarehouseUiState(
            brands = listOf("All") + items.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted(),
            brandFilter = brand,
            items = items.filter { brand == "All" || it.brand == brand },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WarehouseUiState())

    fun setBrandFilter(brand: String) {
        brandFilter.value = brand
    }

    fun setStock(productId: Long, fullPacks: Int, openAmount: Double) {
        viewModelScope.launch { stockRepository.set(productId, fullPacks, openAmount) }
    }
}
