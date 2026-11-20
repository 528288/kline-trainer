package com.stock.trainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeRecordDao {

    @Insert
    suspend fun insert(record: TradeRecordEntity)

    @Query("SELECT * FROM trade_records WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<TradeRecordEntity>

    @Query("DELETE FROM trade_records WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("SELECT * FROM trade_records ORDER BY exitTime DESC")
    fun getAllStream(): Flow<List<TradeRecordEntity>>

    @Query("SELECT COUNT(*) FROM trade_records")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM trade_records WHERE pnl > 0")
    suspend fun winCount(): Int

    @Query("SELECT SUM(pnl) FROM trade_records")
    suspend fun totalPnl(): Double?
}
