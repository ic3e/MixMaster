package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.ProductDao
import com.conwic.mixmaster.data.db.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

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

    suspend fun delete(product: ProductEntity) = productDao.deleteProduct(product)
}
