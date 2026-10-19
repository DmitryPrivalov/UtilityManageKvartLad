package com.example.utilitymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApartmentDao {

    @Query("SELECT * FROM apartments ORDER BY id DESC")
    fun getAll(): Flow<List<ApartmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(apartment: ApartmentEntity): Long

    @Query("DELETE FROM apartments WHERE id = :id")
    suspend fun deleteById(id: Long)
}