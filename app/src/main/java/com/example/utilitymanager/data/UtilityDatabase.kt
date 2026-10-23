package com.example.utilitymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ApartmentEntity::class,
        TemplateEntity::class,
        MonthlyChargeEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class UtilityDatabase : RoomDatabase() {

    abstract fun apartmentDao(): ApartmentDao

    abstract fun templateDao(): TemplateDao

    abstract fun monthlyChargeDao(): MonthlyChargeDao

    companion object {
        @Volatile
        private var INSTANCE: UtilityDatabase? = null

        // Миграция с версии 1 на версию 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // На этом этапе структура базы не меняется.
            }
        }

        // Миграция с версии 2 на версию 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS calculation_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startDay INTEGER NOT NULL,
                        extraSettingsJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    ALTER TABLE apartments
                    ADD COLUMN templateId INTEGER
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): UtilityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UtilityDatabase::class.java,
                    "utility_manager.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4_MONTHLY_CHARGES
                    )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}