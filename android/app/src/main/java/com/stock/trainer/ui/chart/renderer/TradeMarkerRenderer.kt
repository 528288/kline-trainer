package com.stock.trainer.ui.chart.renderer

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.chart.TradeMarker
import com.stock.trainer.ui.theme.AppMarketColors

object TradeMarkerRenderer {

    private val textPaint = Paint().apply {
        color = Color.White.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 18f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun draw(
        drawScope: DrawScope,
        markers: List<TradeMarker>,
        visibleData: List<KLineData>,
        visibleStart: Int,
        chartTop: Float,
        chartBottom: Float,
        chartLeft: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        if (markers.isEmpty() || visibleData.isEmpty()) return

        val high = visibleData.maxOf { it.high }
        val low = visibleData.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val priceRange = high - low
        if (priceRange <= 0.0 || !priceRange.isFinite()) return

        val unit = (chartBottom - chartTop) / priceRange.toFloat()
        val visibleEnd = visibleStart + visibleData.size
        val markerSize = 22f
        val halfSize = markerSize / 2f

        markers.forEach { marker ->
            if (marker.index !in visibleStart until visibleEnd) return@forEach
            if (marker.price <= 0.0 || !marker.price.isFinite()) return@forEach

            val relIdx = marker.index - visibleStart
            val centerX = chartLeft + relIdx * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val priceY = chartTop + (high - marker.price).toFloat() * unit
            val isBuy = marker.side == TradeMarker.Side.BUY
            val color = if (isBuy) colors.bullish else colors.bearish
            val label = if (isBuy) "B" else "S"
            val centerY = if (isBuy) {
                (priceY + halfSize + 6f).coerceAtMost(chartBottom - halfSize)
            } else {
                (priceY - halfSize - 6f).coerceAtLeast(chartTop + halfSize)
            }

            val path = if (isBuy) {
                Path().apply {
                    moveTo(centerX, centerY - halfSize)
                    lineTo(centerX - halfSize, centerY + halfSize)
                    lineTo(centerX + halfSize, centerY + halfSize)
                    close()
                }
            } else {
                Path().apply {
                    moveTo(centerX, centerY + halfSize)
                    lineTo(centerX - halfSize, centerY - halfSize)
                    lineTo(centerX + halfSize, centerY - halfSize)
                    close()
                }
            }

            drawScope.drawPath(path, color)
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                label,
                centerX,
                centerY + 6f,
                textPaint,
            )
            drawScope.drawLine(
                color.copy(alpha = 0.65f),
                start = Offset(centerX, priceY),
                end = Offset(centerX, if (isBuy) centerY - halfSize else centerY + halfSize),
                strokeWidth = 1.2f,
            )
        }
    }
}
