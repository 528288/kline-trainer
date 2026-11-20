package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.ui.theme.AppMarketColors

object KdjRenderer {

    fun draw(
        drawScope: DrawScope,
        k: List<Double?>,
        d: List<Double?>,
        j: List<Double?>,
        panelTop: Float,
        panelBottom: Float,
        panelLeft: Float,
        panelRight: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        val all = (k + d + j).filterNotNull().filter { it.isFinite() }
        if (all.isEmpty()) return

        val maxVal = all.max().coerceAtLeast(100.0)
        val minVal = all.min().coerceAtMost(0.0)
        if (!maxVal.isFinite() || !minVal.isFinite()) return
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        val chartTop = panelTop + 20f
        val chartBottom = panelBottom - 4f
        val chartHeight = chartBottom - chartTop

        // 80 and 20 lines
        val y80 = (chartTop + ((maxVal - 80.0) / range * chartHeight)).toFloat()
        val y20 = (chartTop + ((maxVal - 20.0) / range * chartHeight)).toFloat()
        drawScope.drawLine(colors.grid, androidx.compose.ui.geometry.Offset(panelLeft, y80), androidx.compose.ui.geometry.Offset(panelRight, y80), strokeWidth = 0.5f)
        drawScope.drawLine(colors.grid, androidx.compose.ui.geometry.Offset(panelLeft, y20), androidx.compose.ui.geometry.Offset(panelRight, y20), strokeWidth = 0.5f)

        drawLine(drawScope, k, chartTop, panelLeft, chartHeight, candleWidth, gapWidth, colors.wick, maxVal, range)
        drawLine(drawScope, d, chartTop, panelLeft, chartHeight, candleWidth, gapWidth, colors.line1, maxVal, range)
        drawLine(drawScope, j, chartTop, panelLeft, chartHeight, candleWidth, gapWidth, colors.line3, maxVal, range)
    }

    private fun drawLine(
        drawScope: DrawScope,
        values: List<Double?>,
        chartTop: Float,
        chartLeft: Float,
        chartHeight: Float,
        candleWidth: Float,
        gapWidth: Float,
        color: Color,
        maxVal: Double,
        range: Double,
    ) {
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) return@forEachIndexed
            val x = chartLeft + i * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val y = (chartTop + ((maxVal - v) / range * chartHeight)).toFloat()
            if (!y.isFinite()) return@forEachIndexed
            if (!started) { path.moveTo(x, y); started = true }
            else path.lineTo(x, y)
        }
        drawScope.drawPath(path, color, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
