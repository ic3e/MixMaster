package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ProductsUiState(
    val brands: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val brandFilter: String = "All",
    val categoryFilter: String = "All",
    val visibleProducts: List<ProductEntity> = emptyList(),
)

class ProductsViewModel(
    private val productRepository: ProductRepository,
    private val solutionRepository: SolutionRepository,
) : ViewModel() {

    /** The recipes, listed beside the things they are made of. */
    val solutions: StateFlow<List<SolutionEntity>> = solutionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val brandFilter = MutableStateFlow("All")
    private val categoryFilter = MutableStateFlow("All")

    val uiState: StateFlow<ProductsUiState> = combine(
        productRepository.observeAll(),
        productRepository.observeBrands(),
        productRepository.observeCategories(),
        brandFilter,
        categoryFilter,
    ) { products, brands, categories, brand, category ->
        ProductsUiState(
            brands = listOf("All") + brands,
            categories = listOf("All") + categories,
            brandFilter = brand,
            categoryFilter = category,
            visibleProducts = products.filter {
                (brand == "All" || it.brand == brand) && (category == "All" || it.category == category)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductsUiState())

    fun setBrandFilter(brand: String) = brandFilter.update { brand }

    fun setCategoryFilter(category: String) = categoryFilter.update { category }
}
