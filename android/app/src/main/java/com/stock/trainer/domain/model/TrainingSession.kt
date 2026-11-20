package com.stock.trainer.domain.model

data class TrainingSession(
    val id: String,
    val symbol: String,
    val period: String,
    val startIndex: Int,
    val endIndex: Int,
    val speed: Float = 1f,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val initialBalance: Double = 1.0,
    val finalBalance: Double? = null,
    val totalTrades: Int = 0,
    val winRate: Double? = null,
    val totalPnl: Double = 0.0,
)
