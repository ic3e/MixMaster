package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

data class ProductWithComponents(
    val product: ProductEntity,
    val components: List<ProductComponentEntity>,
)

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY brand, name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isArchived = 0 AND isAddOn = 1 ORDER BY brand, name")
    fun observeAddOns(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM product_components WHERE productId = :productId ORDER BY sortOrder")
    fun observeComponents(productId: Long): Flow<List<ProductComponentEntity>>

    /** Every component row, so an add-on's pack size can be found without a query each. */
    @Query("SELECT * FROM product_components ORDER BY productId, sortOrder")
    fun observeAllComponents(): Flow<List<ProductComponentEntity>>

    @Query("SELECT * FROM product_components WHERE productId = :productId ORDER BY sortOrder")
    suspend fun getComponents(productId: Long): List<ProductComponentEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT DISTINCT brand FROM products WHERE isArchived = 0 ORDER BY brand")
    fun observeBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM products WHERE isArchived = 0 ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    // Offered back when adding a product, so the same thing doesn't get typed three ways.
    @Query("SELECT DISTINCT doseUnitLabel FROM products WHERE isArchived = 0 AND doseUnitLabel != '' ORDER BY doseUnitLabel")
    fun observeDoseUnitLabels(): Flow<List<String>>

    @Query("SELECT DISTINCT label FROM product_components WHERE label != '' ORDER BY label")
    fun observeComponentLabels(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM products WHERE isArchived = 0")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<ProductComponentEntity>)

    @Transaction
    suspend fun insertProductWithComponents(product: ProductEntity, components: List<ProductComponentEntity>): Long {
        val productId = insertProduct(product)
        insertComponents(components.map { it.copy(productId = productId) })
        return productId
    }

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM product_components WHERE productId = :productId")
    suspend fun deleteComponentsFor(productId: Long)

    @Transaction
    suspend fun replaceComponents(productId: Long, components: List<ProductComponentEntity>) {
        deleteComponentsFor(productId)
        insertComponents(components.map { it.copy(productId = productId) })
    }

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun countAll(): Int

    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY brand, name")
    suspend fun getAllSnapshot(): List<ProductEntity>

    @Transaction
    suspend fun getAllWithComponents(): List<ProductWithComponents> =
        getAllSnapshot().map { product -> ProductWithComponents(product, getComponents(product.id)) }
}
