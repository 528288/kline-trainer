package com.stock.trainer.domain.model

import java.util.UUID

sealed class DrawingObject {
    abstract val id: String
    abstract val color: Int

    data class HorizontalLine(
        override val id: String = UUID.randomUUID().toString(),
        val price: Double,
        override val color: Int,
    ) : DrawingObject()

    data class TrendLine(
        override val id: String = UUID.randomUUID().toString(),
        val startIndex: Int,
        val startPrice: Double,
        val endIndex: Int,
        val endPrice: Double,
        override val color: Int,
    ) : DrawingObject()
}
