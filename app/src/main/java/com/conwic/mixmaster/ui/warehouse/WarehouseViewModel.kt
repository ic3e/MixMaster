package com.conwic.mixmaster.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.data.repository.DeliveryRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WarehouseUiState(
    val brands: List<String> = emptyList(),
    val brandFilter: String = "All",
    val items: List<ProductStock> = emptyList(),
    /** Every product, whatever the brand filter says — the count covers the whole shed. */
    val shelf: List<ProductStock> = emptyList(),
) {
    /** What still has to be ordered — anything already on its way is somebody's problem already. */
    val toOrder: List<ProductStock> get() = items.filter { it.stillToOrder > 0.0 }
}

class WarehouseViewModel(
    private val productRepository: ProductRepository,
    private val projectRepository: ProjectRepository,
    private val solutionRepository: SolutionRepository,
    private val stockRepository: StockRepository,
    private val deliveryRepository: DeliveryRepository,
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

    /** Ordered and not here yet, by product — what the shelf is waiting on. */
    val onTheWay: StateFlow<Map<Long, List<DeliveryEntity>>> = deliveryRepository.observeAll()
        .map { rows -> rows.filter { it.arrivedOn == null }.groupBy { it.productId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val shelf = combine(
        productRepository.observeAll(),
        stockRepository.observeAll(),
        bookings,
        onTheWay,
    ) { products, stock, booked, coming ->
        val stockByProduct = stock.associateBy { it.productId }
        products.map { product ->
            productStock(
                product = product,
                stock = stockByProduct[product.id],
                bookings = booked[product.id].orEmpty(),
                deliveries = coming[product.id].orEmpty(),
            )
        }
    }

    val uiState: StateFlow<WarehouseUiState> = combine(shelf, brandFilter) { items, brand ->
        WarehouseUiState(
            brands = listOf("All") + items.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted(),
            brandFilter = brand,
            items = items.filter { brand == "All" || it.brand == brand },
            shelf = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WarehouseUiState())

    fun setBrandFilter(brand: String) {
        brandFilter.value = brand
    }

    fun setStock(productId: Long, fullPacks: Int, openAmount: Double) {
        viewModelScope.launch { stockRepository.set(productId, fullPacks, openAmount) }
    }

    fun adjustPacks(productId: Long, delta: Int) {
        viewModelScope.launch { stockRepository.adjustPacks(productId, delta) }
    }

    fun order(productId: Long, packs: Int, amount: Double, expectedOn: LocalDate, note: String) {
        viewModelScope.launch { deliveryRepository.order(productId, packs, amount, expectedOn, note) }
    }

    fun receive(delivery: DeliveryEntity, packSize: Double) {
        viewModelScope.launch { deliveryRepository.receive(delivery, packSize) }
    }

    fun cancelOrder(id: Long) {
        viewModelScope.launch { deliveryRepository.cancel(id) }
    }
}
