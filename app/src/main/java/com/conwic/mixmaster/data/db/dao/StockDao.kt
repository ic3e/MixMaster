package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.StockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM stock")
    fun observeAll(): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE componentId = :componentId")
    suspend fun getForComponent(componentId: Long): StockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: StockEntity): Long

    @Update
    suspend fun update(stock: StockEntity)
}
