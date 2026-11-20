package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stock.trainer.domain.model.KLineData

object MaRenderer {

    private val ma5Color = Color(0xFFFFCA28)
    private val ma10Color = Color(0xFFFF7043)
    private val ma20Color = Color(0xFFAB47BC)

    fun draw(
        drawScope: DrawScope,
        maValues: List<Double?>,
        data: List<KLineData>,
        color: Color,
        top: Float,
        bottom: Float,
        left: Float,
        candleWidth: Float,
        gapWidth: Float,
    ) {
        if (data.isEmpty()) return
        val high = data.maxOf { it.high }
        val low = data.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val priceRange = high - low
        if (priceRange <= 0 || !priceRange.isFinite()) return

        val chartHeight = bottom - top
        val unit = (chartHeight / priceRange).toFloat()

        val path = Path()
        var started = false

        maValues.forEachIndexed { i, v ->
            if (v == null || !v.isFinite()) return@forEachIndexed
            val x = left + i * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
            val y = top + (high - v).toFloat() * unit
            if (!y.isFinite()) return@forEachIndexed
            if (!started) { path.moveTo(x, y); started = true }
            else path.lineTo(x, y)
        }

        drawScope.drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // keep for backward compat
    fun drawMa5(drawScope: DrawScope, data: List<KLineData>, top: Float, bottom: Float, candleWidth: Float, gapWidth: Float) {
        val values = com.stock.trainer.domain.indicator.IndicatorCalculator.computeMa(data, 5)
        draw(drawScope, values, data, ma5Color, top, bottom, 0f, candleWidth, gapWidth)
    }

    val COLOR_MA5 = Color(0xFFFFCA28)
    val COLOR_MA10 = Color(0xFFFF7043)
    val COLOR_MA20 = Color(0xFFAB47BC)
}
