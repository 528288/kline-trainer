package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.theme.AppMarketColors

object BollRenderer {

    fun draw(
        drawScope: DrawScope,
        upper: List<Double?>,
        middle: List<Double?>,
        lower: List<Double?>,
        data: List<KLineData>,
        chartTop: Float,
        chartBottom: Float,
        chartLeft: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        if (data.isEmpty()) return
        val high = data.maxOf { it.high }
        val low = data.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val priceRange = high - low
        if (priceRange <= 0 || !priceRange.isFinite()) return

        val chartHeight = chartBottom - chartTop
        val unit = (chartHeight / priceRange).toFloat()

        drawBand(drawScope, upper, chartTop, chartLeft, high, unit, candleWidth, gapWidth, colors.line1)
        drawBand(drawScope, middle, chartTop, chartLeft, high, unit, candleWidth, gapWidth, colors.wick)
        drawBand(drawScope, lower, chartTop, chartLeft, high, unit, candleWidth, gapWidth, colors.line1)
    }

    private fun drawBand(
        drawScope: DrawScope,
        values: List<Double?>,
        chartTop: Float,
        chartLeft: Float,
        high: Double,
        unit: Float,
        candleWidth: Float,
        gapWidth: Float,
        color: Color,
    ) {
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) return@forEachIndexed
            val x = chartLeft + i * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val y = chartTop + (high - v).toFloat() * unit
            if (!y.isFinite()) return@forEachIndexed
            if (!started) { path.moveTo(x, y); started = true }
            else path.lineTo(x, y)
        }
        drawScope.drawPath(path, color, style = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
