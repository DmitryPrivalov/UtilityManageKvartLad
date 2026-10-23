package com.example.utilitymanager.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Снимок одной услуги за один расчётный месяц одной квартиры.
 * yearMonth хранится в ISO формате YYYY-MM (например 2026-10).
 * Числа храним десятичными строками без потери точности (не Double).
 * unitPrice и amount будут заполнены после подключения тарифов из шаблонов.
 */
@Entity(
    tableName = "monthly_charges",
    primaryKeys = ["apartmentId", "yearMonth", "serviceKey"],
    indices = [Index(value = ["apartmentId", "yearMonth"])]
)
data class MonthlyChargeEntity(
    val apartmentId: Long,
    val yearMonth: String,
    val serviceKey: String,
    val serviceName: String,
    val unit: String,
    val previousReading: String? = null,
    val currentReading: String? = null,
    val usage: String? = null,
    val unitPrice: String? = null,
    val amount: String? = null
)
