package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY takenAt DESC")
    fun observeForProject(projectId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE roomId = :roomId ORDER BY takenAt DESC")
    fun observeForRoom(roomId: Long): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity): Long

    @Delete
    suspend fun delete(photo: PhotoEntity)
}
