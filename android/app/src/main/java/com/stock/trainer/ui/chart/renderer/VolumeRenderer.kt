package com.stock.trainer.ui.chart.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.theme.AppMarketColors

object VolumeRenderer {

    fun draw(
        drawScope: DrawScope,
        data: List<KLineData>,
        top: Float,
        bottom: Float,
        left: Float,
        candleWidth: Float,
        gapWidth: Float,
        colors: AppMarketColors,
    ) {
        if (data.isEmpty()) return

        val maxVolume = data.maxOf { it.volume }
        if (maxVolume <= 0) return
        if (top.isNaN() || bottom.isNaN() || !top.isFinite() || !bottom.isFinite()) return

        val chartHeight = bottom - top
        val unit = chartHeight / maxVolume.toFloat()

        data.forEachIndexed { index, candle ->
            val x = left + index * (candleWidth + gapWidth) + gapWidth / 2f
            val barHeight = candle.volume.toFloat() * unit
            val y = bottom - barHeight

            val isBullish = candle.close >= candle.open
            val color = if (isBullish) colors.bullishSoft else colors.bearishSoft

            drawScope.drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(candleWidth, barHeight),
            )
        }
    }
}
