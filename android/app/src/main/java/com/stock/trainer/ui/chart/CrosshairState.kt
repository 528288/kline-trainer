package com.stock.trainer.ui.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class CrosshairState {
    var point: CrosshairPoint? by mutableStateOf(null)
        private set

    fun show(index: Int, price: Double) {
        point = CrosshairPoint(index, price)
    }

    fun clear() {
        point = null
    }
}

data class CrosshairPoint(
    val index: Int,
    val price: Double,
)
