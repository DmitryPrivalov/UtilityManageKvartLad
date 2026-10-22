
package com.example.utilitymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM calculation_templates ORDER BY name")
    fun getAll(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(template: TemplateEntity): Long

    @Query("SELECT * FROM calculation_templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("DELETE FROM calculation_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
