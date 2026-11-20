package com.stock.trainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KLineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<KLineEntity>)

    @Query("DELETE FROM kline")
    suspend fun deleteAll()

    @Query("""
        SELECT * FROM kline
        WHERE symbol = :symbol AND period = :period
        ORDER BY timestamp ASC
    """)
    fun getKLineStream(symbol: String, period: String): Flow<List<KLineEntity>>

    @Query("""
        SELECT * FROM kline
        WHERE symbol = :symbol AND period = :period
        ORDER BY timestamp ASC
    """)
    suspend fun getKLine(symbol: String, period: String): List<KLineEntity>

    @Query("SELECT COUNT(*) FROM kline WHERE symbol = :symbol AND period = :period")
    suspend fun count(symbol: String, period: String): Int

    @Query("SELECT DISTINCT symbol FROM kline")
    suspend fun getAllSymbols(): List<String>

    @Query("SELECT DISTINCT period FROM kline")
    suspend fun getAllPeriods(): List<String>

    @Query("""
        SELECT CASE WHEN
            SUM(CASE WHEN period = 'day' THEN 1 ELSE 0 END) >= :minDayCount
            AND SUM(CASE WHEN period = 'week' THEN 1 ELSE 0 END) > 0
            AND SUM(CASE WHEN period = '60min' THEN 1 ELSE 0 END) > 0
        THEN 1 ELSE 0 END
        FROM kline
        WHERE symbol = :symbol
    """)
    suspend fun hasDayWeekMinute60(symbol: String, minDayCount: Int): Int

    @Query("""
        SELECT symbol FROM kline
        WHERE period = :period
        GROUP BY symbol
        HAVING COUNT(*) >= :minCount
    """)
    suspend fun getSymbolsWithAtLeast(period: String, minCount: Int): List<String>

    @Query("""
        SELECT symbol FROM kline
        GROUP BY symbol
        HAVING SUM(CASE WHEN period = 'day' THEN 1 ELSE 0 END) >= :minDayCount
            AND SUM(CASE WHEN period = 'week' THEN 1 ELSE 0 END) > 0
            AND SUM(CASE WHEN period = '60min' THEN 1 ELSE 0 END) > 0
    """)
    suspend fun getSymbolsWithDayWeekMinute60(minDayCount: Int): List<String>

    @Query("SELECT DISTINCT period FROM kline WHERE symbol = :symbol")
    suspend fun getPeriodsForSymbol(symbol: String): List<String>
}
