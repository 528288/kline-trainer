package com.stock.trainer.ui.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

@Stable
class ViewportState {
    var visibleStart by mutableIntStateOf(0)
    var visibleCount by mutableIntStateOf(60)
    var candleWidth by mutableFloatStateOf(8f)
    var gapWidth by mutableFloatStateOf(2f)
    var dataSize by mutableIntStateOf(0)
    private var panRemainder = 0f

    val totalItemWidth: Float get() = candleWidth + gapWidth
    val visibleEnd: Int get() = visibleStart + visibleCount
    private val maxStart: Int get() = maxOf(0, dataSize - visibleCount)

    fun updateDataSize(size: Int) {
        dataSize = size.coerceAtLeast(0)
        clamp()
    }

    fun setWindow(size: Int, count: Int, anchorIndex: Int) {
        dataSize = size.coerceAtLeast(0)
        visibleCount = count.coerceAtLeast(1).coerceAtMost(dataSize.coerceAtLeast(1))
        visibleStart = (anchorIndex - visibleCount + 1).coerceIn(0, maxStart)
        resetPan()
    }

    fun setWindowPreservingStart(size: Int, count: Int) {
        dataSize = size.coerceAtLeast(0)
        visibleCount = count.coerceAtLeast(1).coerceAtMost(dataSize.coerceAtLeast(1))
        clamp()
    }

    fun resetPan() {
        panRemainder = 0f
    }

    fun panBy(deltaX: Float) {
        panRemainder += deltaX * PAN_SPEED / totalItemWidth.coerceAtLeast(1f)
        val step = panRemainder.toInt()
        if (step == 0) return
        visibleStart -= step
        panRemainder -= step
        clamp()
    }

    fun zoomIn() {
        if (visibleCount > 15) {
            visibleCount -= 5
            visibleStart += 2
            clamp()
        }
    }

    fun zoomOut() {
        if (visibleCount < 200) {
            visibleCount += 10
            visibleStart -= 5
            clamp()
        }
    }

    private fun clamp() {
        visibleCount = visibleCount.coerceAtLeast(1)
        visibleStart = visibleStart.coerceIn(0, maxStart)
    }

    private companion object {
        const val PAN_SPEED = 3.6f
    }
}
