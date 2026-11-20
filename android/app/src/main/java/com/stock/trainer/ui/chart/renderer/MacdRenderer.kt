package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.ui.theme.AppMarketColors

object MacdRenderer {

    fun draw(
        drawScope: DrawScope,
        dif: List<Double?>,
        dea: List<Double?>,
        histogram: List<Double?>,
        panelTop: Float,
        panelBottom: Float,
        panelLeft: Float,
        panelRight: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        val all = (dif + dea + histogram).filterNotNull().filter { it.isFinite() }
        if (all.isEmpty()) return

        val maxVal = all.max()
        val minVal = all.min()
        if (!maxVal.isFinite() || !minVal.isFinite()) return
        val range = maxVal - minVal
        if (range <= 0 || !range.isFinite()) return

        val chartTop = panelTop + 20f
        val chartBottom = panelBottom - 4f
        val chartHeight = chartBottom - chartTop
        val unit = (chartHeight / range).toFloat()

        val zeroY = (chartTop + (maxVal / range * chartHeight)).toFloat().coerceIn(chartTop, chartBottom)

        // histogram bars
        histogram.forEachIndexed { i, value ->
            if (value == null || !value.isFinite()) return@forEachIndexed
            val x = panelLeft + i * (candleWidth + gapWidth) + gapWidth / 2f
            val barTop = (zeroY - (value.toFloat() * unit)).coerceIn(chartTop, chartBottom)
            drawScope.drawRect(
                color = if (value >= 0) colors.bullishSoft else colors.bearishSoft,
                topLeft = Offset(x, if (value >= 0) barTop else zeroY),
                size = Size(candleWidth, kotlin.math.abs(barTop - zeroY).coerceAtLeast(1f)),
            )
        }

        // DIF line
        drawLine(drawScope, dif, chartTop, chartBottom, panelLeft, candleWidth, gapWidth, colors.wick, maxVal, minVal, chartHeight)

        // DEA line
        drawLine(drawScope, dea, chartTop, chartBottom, panelLeft, candleWidth, gapWidth, colors.line1, maxVal, minVal, chartHeight)
    }

    private fun drawLine(
        drawScope: DrawScope,
        values: List<Double?>,
        chartTop: Float,
        chartBottom: Float,
        panelLeft: Float,
        candleWidth: Float,
        gapWidth: Float,
        color: Color,
        maxVal: Double,
        minVal: Double,
        chartHeight: Float,
    ) {
        val path = Path()
        var started = false
        val range = maxVal - minVal
        values.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) return@forEachIndexed
            val x = panelLeft + i * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val y = (chartTop + ((maxVal - v) / range * chartHeight)).toFloat()
            if (!y.isFinite()) return@forEachIndexed
            if (!started) { path.moveTo(x, y); started = true }
            else path.lineTo(x, y)
        }
        drawScope.drawPath(path, color, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
