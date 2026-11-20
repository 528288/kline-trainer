package com.stock.trainer.ui.chart.renderer

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.theme.AppMarketColors

object GridRenderer {

    private val gridColor = Color(0x30AAAAAA)

    private val textPaint by lazy {
        Paint().apply {
            color = Color(0xFF888888).toArgb()
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
    }

    fun draw(
        drawScope: DrawScope,
        data: List<KLineData>,
        top: Float,
        bottom: Float,
        left: Float,
        right: Float,
        colors: AppMarketColors? = null,
    ) {
        if (data.isEmpty()) return

        val high = data.maxOf { it.high }
        val low = data.minOf { it.low }
        if (!high.isFinite() || !low.isFinite()) return
        val range = high - low
        if (range <= 0 || !range.isFinite()) return

        val gridCount = 5
        val rowHeight = (bottom - top) / gridCount

        for (i in 0..gridCount) {
            val y = top + rowHeight * i
            val price = high - (range / gridCount) * i

            drawScope.drawLine(
                color = colors?.grid ?: gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 0.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )

            textPaint.color = (colors?.chartText ?: Color(0xFF888888)).toArgb()
            drawScope.drawContext.canvas.nativeCanvas.drawText(
                String.format("%.2f", price),
                left + 4f,
                y + textPaint.textSize / 3f,
                textPaint,
            )
        }
    }
}
