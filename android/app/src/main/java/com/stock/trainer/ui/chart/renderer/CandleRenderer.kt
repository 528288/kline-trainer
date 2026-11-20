package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.theme.AppMarketColors

object CandleRenderer {

    fun draw(
        drawScope: DrawScope,
        data: List<KLineData>,
        top: Float,
        bottom: Float,
        left: Float,
        candleWidth: Float,
        gapWidth: Float,
        isStepOne: Boolean = false,
        colors: AppMarketColors,
    ) {
        if (data.isEmpty()) return

        val high = data.maxOf { it.high }
        val low = data.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val priceRange = high - low
        if (priceRange <= 0 || !priceRange.isFinite()) return

        val chartHeight = bottom - top
        val unit = (chartHeight / priceRange).toFloat()

        data.forEachIndexed { index, candle ->
            val x = left + index * (candleWidth + gapWidth) + gapWidth / 2f
            val centerX = x + candleWidth / 2f

            val isLastAndStepOne = isStepOne && index == data.lastIndex

            if (isLastAndStepOne) {
                // draw a horizontal dash at the open price only
                val openY = top + (high - candle.open).toFloat() * unit
                drawScope.drawLine(
                    color = colors.warning,
                    start = Offset(centerX - candleWidth / 2f, openY),
                    end = Offset(centerX + candleWidth / 2f, openY),
                    strokeWidth = 2f,
                )
            } else {
                val highY = top + (high - candle.high).toFloat() * unit
                val lowY = top + (high - candle.low).toFloat() * unit
                val openY = top + (high - candle.open).toFloat() * unit
                val closeY = top + (high - candle.close).toFloat() * unit

                val isBullish = candle.close >= candle.open
                // 柱色用 subdued 档（>=3:1 已实测），文字/数字才用高对比档
                val color = if (isBullish) colors.bullishSubdued else colors.bearishSubdued

                // wick upper
                val upperWickTop = highY
                val upperWickBottom = if (isBullish) closeY else openY
                drawScope.drawLine(colors.wick, Offset(centerX, upperWickTop), Offset(centerX, upperWickBottom), strokeWidth = 1f)

                // wick lower
                val lowerWickTop = if (isBullish) openY else closeY
                val lowerWickBottom = lowY
                drawScope.drawLine(colors.wick, Offset(centerX, lowerWickTop), Offset(centerX, lowerWickBottom), strokeWidth = 1f)

                // body：<3px 的实体必须实心，否则描边会把 K 线吃掉
                val bodyTop = if (isBullish) closeY else openY
                val bodyBottom = if (isBullish) openY else closeY
                val bodyHeight = maxOf(bodyBottom - bodyTop, 1f)
                if (candleWidth >= 3f && bodyHeight >= 3f) {
                    drawScope.drawRect(
                        color = color,
                        topLeft = Offset(x, bodyTop),
                        size = Size(candleWidth, bodyHeight),
                        style = Stroke(width = 1.2f),
                    )
                } else {
                    drawScope.drawRect(color, Offset(x, bodyTop), Size(candleWidth, bodyHeight))
                }
            }
        }
    }
}
