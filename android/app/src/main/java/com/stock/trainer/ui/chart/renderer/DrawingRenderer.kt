package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.stock.trainer.domain.model.DrawingObject
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.theme.AppMarketColors
import kotlin.math.abs

object DrawingRenderer {

    private val dotEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))

    fun draw(
        drawScope: DrawScope,
        drawings: List<DrawingObject>,
        pendingPoints: List<Pair<Int, Double>>,
        visibleData: List<KLineData>,
        visibleStart: Int,
        chartTop: Float,
        chartBottom: Float,
        chartLeft: Float,
        chartRight: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        if (visibleData.isEmpty()) return

        val high = visibleData.maxOf { it.high }
        val low = visibleData.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val priceRange = high - low
        if (priceRange <= 0 || !priceRange.isFinite()) return

        val unit = (chartBottom - chartTop) / priceRange.toFloat()

        drawings.forEach { drawObject(drawScope, it, visibleStart, chartTop, chartBottom, chartLeft, chartRight, candleWidth, gapWidth, unit, high) }

        drawPending(drawScope, pendingPoints, visibleStart, chartTop, chartLeft, high, unit, candleWidth, gapWidth, colors)
    }

    private fun drawObject(
        drawScope: DrawScope,
        obj: DrawingObject,
        visibleStart: Int,
        chartTop: Float,
        chartBottom: Float,
        chartLeft: Float,
        chartRight: Float,
        candleWidth: Float,
        gapWidth: Float,
        unit: Float,
        high: Double,
    ) {
        val color = Color(obj.color)
        when (obj) {
            is DrawingObject.HorizontalLine -> {
                val y = chartTop + (high - obj.price).toFloat() * unit
                if (y < chartTop || y > chartBottom) return
                drawScope.drawLine(color, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1.5f, pathEffect = dotEffect)
            }
            is DrawingObject.TrendLine -> {
                val startX = indexToX(obj.startIndex, visibleStart, chartLeft, candleWidth, gapWidth)
                val startY = chartTop + (high - obj.startPrice).toFloat() * unit
                val endX = indexToX(obj.endIndex, visibleStart, chartLeft, candleWidth, gapWidth)
                val endY = chartTop + (high - obj.endPrice).toFloat() * unit

                val clamped = clipInfiniteLine(startX, startY, endX, endY, chartLeft, chartRight, chartTop, chartBottom)
                if (clamped != null) {
                    val (x1, y1, x2, y2) = clamped
                    drawScope.drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5f)
                }
                drawAnchor(drawScope, color, startX, startY, chartLeft, chartRight, chartTop, chartBottom)
                drawAnchor(drawScope, color, endX, endY, chartLeft, chartRight, chartTop, chartBottom)
            }
        }
    }

    private fun drawPending(
        drawScope: DrawScope,
        points: List<Pair<Int, Double>>,
        visibleStart: Int,
        chartTop: Float,
        chartLeft: Float,
        high: Double,
        unit: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        if (points.isEmpty()) return
        val color = colors.warning

        // draw placed points as dots
        points.forEach { (idx, price) ->
            val x = indexToX(idx, visibleStart, chartLeft, candleWidth, gapWidth)
            val y = chartTop + (high - price).toFloat() * unit
            drawScope.drawCircle(color, 6f, Offset(x, y))
            drawScope.drawCircle(Color.White.copy(alpha = 0.85f), 8f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }

        // draw connecting line from last point to current (if applicable)
        if (points.size == 2) {
            val (i1, p1) = points[0]
            val (i2, p2) = points[1]
            val x1 = indexToX(i1, visibleStart, chartLeft, candleWidth, gapWidth)
            val y1 = chartTop + (high - p1).toFloat() * unit
            val x2 = indexToX(i2, visibleStart, chartLeft, candleWidth, gapWidth)
            val y2 = chartTop + (high - p2).toFloat() * unit
            drawScope.drawLine(color.copy(alpha = 0.6f), Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5f, pathEffect = dotEffect)
        }
    }

    private fun indexToX(index: Int, visibleStart: Int, chartLeft: Float, candleWidth: Float, gapWidth: Float): Float {
        val relIdx = index - visibleStart
        return chartLeft + relIdx * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
    }

    private fun drawAnchor(
        drawScope: DrawScope,
        color: Color,
        x: Float,
        y: Float,
        chartLeft: Float,
        chartRight: Float,
        chartTop: Float,
        chartBottom: Float,
    ) {
        if (x !in chartLeft..chartRight || y !in chartTop..chartBottom) return
        val center = Offset(x, y)
        drawScope.drawCircle(color, 5f, center)
        drawScope.drawCircle(Color.White.copy(alpha = 0.85f), 7f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3f))
    }

    /**
     * Clip the infinite line through two anchor points to the visible chart rectangle.
     */
    private fun clipInfiniteLine(
        x1: Float, y1: Float, x2: Float, y2: Float,
        left: Float, right: Float, top: Float, bottom: Float,
    ): List<Float>? {
        val dx = x2 - x1
        val dy = y2 - y1
        val epsilon = 0.0001f

        if (abs(dx) < epsilon && abs(dy) < epsilon) return null

        if (abs(dx) < epsilon) {
            return if (x1 in left..right) listOf(x1, top, x1, bottom) else null
        }
        if (abs(dy) < epsilon) {
            return if (y1 in top..bottom) listOf(left, y1, right, y1) else null
        }

        val candidates = mutableListOf<Offset>()

        fun addIfInside(point: Offset) {
            if (point.x in left..right && point.y in top..bottom) {
                if (candidates.none { abs(it.x - point.x) < epsilon && abs(it.y - point.y) < epsilon }) {
                    candidates.add(point)
                }
            }
        }

        val yAtLeft = y1 + (left - x1) * dy / dx
        addIfInside(Offset(left, yAtLeft))

        val yAtRight = y1 + (right - x1) * dy / dx
        addIfInside(Offset(right, yAtRight))

        val xAtTop = x1 + (top - y1) * dx / dy
        addIfInside(Offset(xAtTop, top))

        val xAtBottom = x1 + (bottom - y1) * dx / dy
        addIfInside(Offset(xAtBottom, bottom))

        if (candidates.size < 2) return null

        var first = candidates[0]
        var second = candidates[1]
        var maxDistance = distanceSquared(first, second)
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val distance = distanceSquared(candidates[i], candidates[j])
                if (distance > maxDistance) {
                    maxDistance = distance
                    first = candidates[i]
                    second = candidates[j]
                }
            }
        }

        return listOf(first.x, first.y, second.x, second.y)
    }

    private fun distanceSquared(first: Offset, second: Offset): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return dx * dx + dy * dy
    }
}
