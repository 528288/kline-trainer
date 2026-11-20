package com.stock.trainer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trade_records")
data class TradeRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val direction: String,   // "LONG" / "SHORT"
    val entryPrice: Double,
    val exitPrice: Double,
    val entryTime: Long,
    val exitTime: Long,
    val quantity: Int,
    val pnl: Double,
    val pnlPercent: Double,
    val note: String,
)