package com.stock.trainer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val period: String,
    val startIndex: Int,
    val endIndex: Int,
    val speed: Float,
    val startedAt: Long,
    val finishedAt: Long?,
    val initialBalance: Double,
    val finalBalance: Double?,
    val totalTrades: Int,
    val winRate: Double?,
    val totalPnl: Double,
)