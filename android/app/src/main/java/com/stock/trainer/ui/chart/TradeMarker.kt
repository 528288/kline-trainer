package com.stock.trainer.ui.chart

data class TradeMarker(
    val index: Int,
    val price: Double,
    val side: Side,
) {
    enum class Side { BUY, SELL }
}
