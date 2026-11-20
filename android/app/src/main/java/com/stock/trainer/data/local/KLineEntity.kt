package com.stock.trainer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kline",
    indices = [
        Index(value = ["symbol", "period"]),
        Index(value = ["symbol", "period", "timestamp"], unique = true),
    ]
)
data class KLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val period: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)