
package com.example.utilitymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ApartmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UtilityDatabase : RoomDatabase() {

    abstract fun apartmentDao(): ApartmentDao

    companion object {
        @Volatile
        private var INSTANCE: UtilityDatabase? = null

        fun getDatabase(context: Context): UtilityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UtilityDatabase::class.java,
                    "utility_manager.db"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
