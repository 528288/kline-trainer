package com.stock.trainer.domain.model

data class KLineData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
) {
    fun isValid(): Boolean {
        return open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() && high >= low
    }
}