package com.stock.trainer.domain.model

data class TradeRecord(
    val id: String,
    val sessionId: String,
    val direction: Direction,
    val entryPrice: Double,
    val exitPrice: Double,
    val entryTime: Long,
    val exitTime: Long,
    val quantity: Int,
    val pnl: Double,
    val pnlPercent: Double,
    val note: String = "",
) {
    enum class Direction { LONG, SHORT }
}