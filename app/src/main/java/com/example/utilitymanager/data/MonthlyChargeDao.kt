package com.example.utilitymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyChargeDao {
    @Query("""
    DELETE FROM monthly_charges
    WHERE apartmentId = :apartmentId
    AND yearMonth < :cutoffMonth
""")
    suspend fun deleteBeforeMonth(
        apartmentId: Long,
        cutoffMonth: String
    )
    @Query("SELECT * FROM monthly_charges WHERE apartmentId = :apartmentId ORDER BY yearMonth DESC, serviceKey")
    fun observeByApartment(apartmentId: Long): Flow<List<MonthlyChargeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<MonthlyChargeEntity>)

    @Query("DELETE FROM monthly_charges WHERE apartmentId = :apartmentId AND yearMonth = :yearMonth AND serviceKey IN (:serviceKeys)")
    suspend fun deleteServicesForMonth(apartmentId: Long, yearMonth: String, serviceKeys: List<String>)

    @Query("DELETE FROM monthly_charges WHERE apartmentId = :apartmentId")
    suspend fun deleteByApartment(apartmentId: Long)

    /**
     * Атомарно заменяем только счётчиковые услуги указанного месяца.
     * Дополнительные начисления из будущих шаблонов не стираем.
     */
    @Transaction
    suspend fun replaceMeterReadings(
        apartmentId: Long,
        yearMonth: String,
        meterKeys: List<String>,
        rows: List<MonthlyChargeEntity>
    ) {
        require(rows.all {
            it.apartmentId == apartmentId && it.yearMonth == yearMonth && it.serviceKey in meterKeys
        }) { "Некорректная квартира, месяц или ключ услуги" }
        if (meterKeys.isNotEmpty()) deleteServicesForMonth(apartmentId, yearMonth, meterKeys)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    /** Единая транзакция: сохраняем расчёты из выбранного шаблона за месяц. */
    @Transaction
    suspend fun replaceCalculatedMonth(
        apartmentId: Long,
        yearMonth: String,
        managedKeys: List<String>,
        rows: List<MonthlyChargeEntity>
    ) {
        require(rows.all { it.apartmentId == apartmentId && it.yearMonth == yearMonth &&
            it.serviceKey in managedKeys }) { "Некорректная запись начисления" }
        if (managedKeys.isNotEmpty()) {
            deleteServicesForMonth(apartmentId, yearMonth, managedKeys.distinct())
        }
        if (rows.isNotEmpty()) insertAll(rows)
    }

}
