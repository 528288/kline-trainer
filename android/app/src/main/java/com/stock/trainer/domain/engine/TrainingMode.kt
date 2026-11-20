package com.stock.trainer.domain.engine

import com.stock.trainer.domain.model.TradeRecord

enum class PlayMode(val label: String) {
    MANUAL("手动"),
    AUTO("自动"),
}

enum class AdvanceMode(val label: String) {
    SINGLE_STEP("单步"),
    TWO_STEP("双步"),
}

data class BidOrder(
    val direction: TradeRecord.Direction,
    val quantity: Int,
)
