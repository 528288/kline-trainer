package com.stock.trainer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSessionDao {

    @Insert
    suspend fun insert(session: TrainingSessionEntity)

    @Query("UPDATE training_sessions SET finishedAt=:finishedAt, finalBalance=:finalBalance, totalTrades=:totalTrades, winRate=:winRate, totalPnl=:totalPnl WHERE id=:id")
    suspend fun finishSession(
        id: String,
        finishedAt: Long,
        finalBalance: Double,
        totalTrades: Int,
        winRate: Double,
        totalPnl: Double,
    )

    @Query("SELECT * FROM training_sessions ORDER BY startedAt DESC")
    fun getAllStream(): Flow<List<TrainingSessionEntity>>

    @Query("SELECT * FROM training_sessions WHERE id = :id")
    suspend fun getById(id: String): TrainingSessionEntity?

    @Query("DELETE FROM training_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT DISTINCT symbol FROM training_sessions WHERE period = :period")
    suspend fun getTrainedSymbols(period: String): List<String>
}
