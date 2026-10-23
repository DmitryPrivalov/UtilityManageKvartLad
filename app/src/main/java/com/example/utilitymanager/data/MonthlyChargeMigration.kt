package com.example.utilitymanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Выполнить, когда в UtilityDatabase добавлена MonthlyChargeEntity и версия поднята с 3 до 4.
 * Миграция создаёт только новую таблицу: существующие квартиры и шаблоны сохраняются.
 */
val MIGRATION_3_4_MONTHLY_CHARGES = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `monthly_charges` (
                `apartmentId` INTEGER NOT NULL,
                `yearMonth` TEXT NOT NULL,
                `serviceKey` TEXT NOT NULL,
                `serviceName` TEXT NOT NULL,
                `unit` TEXT NOT NULL,
                `previousReading` TEXT,
                `currentReading` TEXT,
                `usage` TEXT,
                `unitPrice` TEXT,
                `amount` TEXT,
                PRIMARY KEY(`apartmentId`, `yearMonth`, `serviceKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_monthly_charges_apartmentId_yearMonth` " +
                "ON `monthly_charges` (`apartmentId`, `yearMonth`)"
        )
    }
}
