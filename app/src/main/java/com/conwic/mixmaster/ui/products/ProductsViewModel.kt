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
    val search: String = "",
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
    // Two dropdowns are no way to find one bag in a catalogue this size, and the tour has been
    // promising a search since the first build.
    private val search = MutableStateFlow("")

    private val filters = combine(brandFilter, categoryFilter, search) { brand, category, text ->
        Triple(brand, category, text)
    }

    val uiState: StateFlow<ProductsUiState> = combine(
        productRepository.observeAll(),
        productRepository.observeBrands(),
        productRepository.observeCategories(),
        filters,
    ) { products, brands, categories, (brand, category, text) ->
        val needle = text.trim()
        ProductsUiState(
            brands = listOf("All") + brands,
            categories = listOf("All") + categories,
            brandFilter = brand,
            categoryFilter = category,
            search = text,
            visibleProducts = products.filter {
                (brand == "All" || it.brand == brand) &&
                    (category == "All" || it.category == category) &&
                    (needle.isBlank() || it.matches(needle))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductsUiState())

    fun setBrandFilter(brand: String) = brandFilter.update { brand }

    fun setSearch(value: String) = search.update { value }

    fun setCategoryFilter(category: String) = categoryFilter.update { category }
}

/**
 * Whether a product answers to what was typed.
 *
 * Brand and name together, because a search for "ardex k 301" is a brand and a name run
 * together, and case-insensitively, because nobody types a capital on site.
 */
fun ProductEntity.matches(needle: String): Boolean =
    "$brand $name $category".contains(needle, ignoreCase = true)

fun SolutionEntity.matches(needle: String): Boolean =
    "$brand $name $coatName $category".contains(needle, ignoreCase = true)
