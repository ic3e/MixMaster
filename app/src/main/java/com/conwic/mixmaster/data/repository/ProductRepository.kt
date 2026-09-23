package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.ProductDao
import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ProductRepository(private val productDao: ProductDao) {

    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

    /** Colours, admixtures — anything added to another product's mix. */
    fun observeAddOns(): Flow<List<ProductEntity>> = productDao.observeAddOns()

    fun observeAllComponents(): Flow<List<ProductComponentEntity>> = productDao.observeAllComponents()

    fun observeById(id: Long): Flow<ProductEntity?> = productDao.observeById(id)

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    /** A bought item on its own: no mix, so no components. */
    suspend fun saveProduct(product: ProductEntity): Long =
        if (product.id == 0L) productDao.insertProduct(product) else {
            productDao.updateProduct(product)
            product.id
        }

    fun observeBrands(): Flow<List<String>> = productDao.observeBrands()

    fun observeCategories(): Flow<List<String>> = productDao.observeCategories()

    fun observeDoseUnitLabels(): Flow<List<String>> = productDao.observeDoseUnitLabels()

    fun observeComponentLabels(): Flow<List<String>> = productDao.observeComponentLabels()

    fun observeCount(): Flow<Int> = productDao.observeCount()

    fun observeWithComponents(productId: Long): Flow<ProductWithComponents?> =
        productDao.observeById(productId).flatMapLatest { product ->
            if (product == null) {
                flowOf(null)
            } else {
                productDao.observeComponents(productId).map { components ->
                    ProductWithComponents(product, components)
                }
            }
        }

    suspend fun getWithComponents(productId: Long): ProductWithComponents? {
        val product = productDao.getById(productId) ?: return null
        return ProductWithComponents(product, productDao.getComponents(productId))
    }

    suspend fun getAllWithComponents(): List<ProductWithComponents> = productDao.getAllWithComponents()

    suspend fun save(product: ProductEntity, components: List<ProductComponentEntity>): Long {
        return if (product.id == 0L) {
            productDao.insertProductWithComponents(product, components)
        } else {
            productDao.updateProduct(product)
            productDao.replaceComponents(product.id, components)
            product.id
        }
    }

    suspend fun delete(product: ProductEntity) = productDao.deleteProduct(product)

}
