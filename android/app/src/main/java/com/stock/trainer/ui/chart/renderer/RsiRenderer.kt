package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.ui.theme.AppMarketColors

object RsiRenderer {

    fun draw(
        drawScope: DrawScope,
        rsi: List<Double?>,
        panelTop: Float,
        panelBottom: Float,
        panelLeft: Float,
        panelRight: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        val valid = rsi.filterNotNull().filter { it.isFinite() }
        if (valid.isEmpty()) return

        val chartTop = panelTop + 20f
        val chartBottom = panelBottom - 4f
        val chartHeight = chartBottom - chartTop

        // overbought zone (70-100)
        val obTop = chartTop + chartHeight * 0f
        val obBottom = chartTop + chartHeight * 0.3f
        drawScope.drawRect(colors.bullishSoft.copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(panelLeft, obTop), size = androidx.compose.ui.geometry.Size(panelRight - panelLeft, obBottom - obTop))

        // oversold zone (0-30)
        val osTop = chartTop + chartHeight * 0.7f
        val osBottom = chartBottom
        drawScope.drawRect(colors.bearishSoft.copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(panelLeft, osTop), size = androidx.compose.ui.geometry.Size(panelRight - panelLeft, osBottom - osTop))

        // 70 and 30 lines
        val y70 = chartTop + chartHeight * 0.3f
        val y30 = chartTop + chartHeight * 0.7f
        drawScope.drawLine(colors.grid, androidx.compose.ui.geometry.Offset(panelLeft, y70), androidx.compose.ui.geometry.Offset(panelRight, y70), strokeWidth = 0.5f)
        drawScope.drawLine(colors.grid, androidx.compose.ui.geometry.Offset(panelLeft, y30), androidx.compose.ui.geometry.Offset(panelRight, y30), strokeWidth = 0.5f)

        // RSI line
        val path = Path()
        var started = false
        rsi.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) return@forEachIndexed
            val x = panelLeft + i * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val y = (chartTop + ((100.0 - v) / 100.0 * chartHeight)).toFloat()
            if (!y.isFinite()) return@forEachIndexed
            if (!started) { path.moveTo(x, y); started = true }
            else path.lineTo(x, y)
        }
        drawScope.drawPath(path, colors.line3, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
