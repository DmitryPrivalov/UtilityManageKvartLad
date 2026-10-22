
package com.example.utilitymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apartments")
data class ApartmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val number: String,
    val address: String,
    val tenant: String,
    val templateId: Long? = null
)
