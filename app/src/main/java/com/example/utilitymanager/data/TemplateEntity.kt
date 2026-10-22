
package com.example.utilitymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    // День начала расчётного периода
    val startDay: Int = 22,

    // Резерв для будущих настроек шаблона
    val extraSettingsJson: String = "{}"
)
