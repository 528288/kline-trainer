package com.stock.trainer.ui.chart

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import com.stock.trainer.domain.indicator.IndicatorCalculator
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.chart.renderer.BollRenderer
import com.stock.trainer.ui.chart.renderer.CandleRenderer
import com.stock.trainer.ui.chart.renderer.DrawingRenderer
import com.stock.trainer.ui.chart.renderer.GridRenderer
import com.stock.trainer.ui.chart.renderer.KdjRenderer
import com.stock.trainer.ui.chart.renderer.MaRenderer
import com.stock.trainer.ui.chart.renderer.MacdRenderer
import com.stock.trainer.ui.chart.renderer.RsiRenderer
import com.stock.trainer.ui.chart.renderer.TradeMarkerRenderer
import com.stock.trainer.ui.chart.renderer.VolumeRenderer
import com.stock.trainer.ui.theme.LocalMarketColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CandlestickChart(
    data: List<KLineData>,
    viewportState: ViewportState = remember { ViewportState() },
    showVolume: Boolean = true,
    indicators: IndicatorDisplay = remember { IndicatorDisplay() },
    drawingState: DrawingState = remember { DrawingState() },
    crosshairState: CrosshairState = remember { CrosshairState() },
    tradeMarkers: List<TradeMarker> = emptyList(),
    isStepOne: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(data.size, data.firstOrNull()?.timestamp, data.lastOrNull()?.timestamp) {
        viewportState.updateDataSize(data.size)
        crosshairState.clear()
    }

    val ma5Values = remember(data, indicators.showMa5, indicators.ma5Period) {
        if (indicators.showMa5) IndicatorCalculator.computeMa(data, indicators.ma5Period) else emptyList()
    }
    val ma10Values = remember(data, indicators.showMa10, indicators.ma10Period) {
        if (indicators.showMa10) IndicatorCalculator.computeMa(data, indicators.ma10Period) else emptyList()
    }
    val ma20Values = remember(data, indicators.showMa20, indicators.ma20Period) {
        if (indicators.showMa20) IndicatorCalculator.computeMa(data, indicators.ma20Period) else emptyList()
    }
    val bollResult = remember(data, indicators.showBoll, indicators.bollPeriod, indicators.bollMultiplier) {
        if (indicators.showBoll) IndicatorCalculator.computeBoll(data, indicators.bollPeriod, indicators.bollMultiplier.toDouble())
        else null
    }
    val macdResult = remember(data, indicators.showMacd, indicators.macdFast, indicators.macdSlow, indicators.macdSignal) {
        if (indicators.showMacd) IndicatorCalculator.computeMacd(data, indicators.macdFast, indicators.macdSlow, indicators.macdSignal)
        else null
    }
    val rsiValues = remember(data, indicators.showRsi, indicators.rsiPeriod) {
        if (indicators.showRsi) IndicatorCalculator.computeRsi(data, indicators.rsiPeriod) else emptyList()
    }
    val kdjResult = remember(data, indicators.showKdj, indicators.kdjN, indicators.kdjK, indicators.kdjD) {
        if (indicators.showKdj) IndicatorCalculator.computeKdj(data, indicators.kdjN, indicators.kdjK, indicators.kdjD)
        else null
    }

    val density = LocalDensity.current.density
    val subPanelCount = listOf(showVolume, indicators.showMacd, indicators.showRsi, indicators.showKdj).count { it }
    val kLineRatio = (1f - subPanelCount * 0.13f).toFloat()
    val marketColors = LocalMarketColors.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(marketColors.chartBackground)
            .pointerInput(drawingState.isDrawing) {
                if (!drawingState.isDrawing) {
                    detectDragGestures(
                        onDragStart = { viewportState.resetPan() },
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount.x != 0f) {
                            viewportState.panBy(dragAmount.x)
                        }
                    }
                }
            }
            .pointerInput(drawingState.isDrawing, subPanelCount, data.size) {
                detectTapGestures { tapOffset ->
                    val totalHeight = size.height
                    val kLineBottom = totalHeight * kLineRatio
                    val marginLeft = 60f
                    val marginRight = 12f
                    val kLineTop = 4f
                    val kLineChartBottom = kLineBottom - 4f
                    val chartRight = size.width - marginRight
                    if (tapOffset.x < marginLeft || tapOffset.x > chartRight) return@detectTapGestures
                    if (tapOffset.y < kLineTop || tapOffset.y > kLineChartBottom) return@detectTapGestures

                    val relIdx = ((tapOffset.x - marginLeft) / viewportState.totalItemWidth).toInt()
                    val absIdx = viewportState.visibleStart + relIdx
                    if (absIdx !in data.indices) return@detectTapGestures

                    val safeStart = viewportState.visibleStart.coerceIn(0, data.size)
                    val safeEnd = minOf(viewportState.visibleEnd, data.size).coerceAtLeast(safeStart)
                    val visibleData = if (safeStart < safeEnd) data.subList(safeStart, safeEnd) else emptyList()
                    if (visibleData.isEmpty()) return@detectTapGestures
                    val high = visibleData.maxOf { it.high }
                    val low = visibleData.minOf { it.low }
                    val range = high - low
                    if (range <= 0) return@detectTapGestures

                    val price = high - (tapOffset.y - kLineTop) / (kLineChartBottom - kLineTop) * range
                    if (drawingState.isDrawing) {
                        drawingState.onKLineTap(absIdx, price)
                    } else {
                        crosshairState.show(absIdx, price)
                    }
                }
            }
    ) {
        val totalHeight = size.height
        val marginLeft = 60f
        val marginRight = 12f

        val subPanelHeight = totalHeight * 0.13f
        val kLineBottom = totalHeight * kLineRatio
        // 画布坐标是像素不是 dp：按 density 缩放面板标签字号，避免高 DPI 下小字糊成一团
        val labelDensity = density

        val visibleStart = viewportState.visibleStart.coerceIn(0, (data.size - 1).coerceAtLeast(0))
        val visibleCount = viewportState.visibleCount
        val visibleEnd = minOf(visibleStart + visibleCount, data.size).coerceAtLeast(visibleStart)
        val visibleData = if (data.isNotEmpty() && visibleStart < visibleEnd) {
            data.subList(visibleStart, visibleEnd)
        } else emptyList()
        val visibleMa5 = ma5Values.visibleSlice(visibleStart, visibleEnd)
        val visibleMa10 = ma10Values.visibleSlice(visibleStart, visibleEnd)
        val visibleMa20 = ma20Values.visibleSlice(visibleStart, visibleEnd)
        val visibleBollUpper = bollResult?.upper?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleBollMiddle = bollResult?.middle?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleBollLower = bollResult?.lower?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleMacdDif = macdResult?.dif?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleMacdDea = macdResult?.dea?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleMacdHistogram = macdResult?.histogram?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleRsi = rsiValues.visibleSlice(visibleStart, visibleEnd)
        val visibleK = kdjResult?.k?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleD = kdjResult?.d?.visibleSlice(visibleStart, visibleEnd).orEmpty()
        val visibleJ = kdjResult?.j?.visibleSlice(visibleStart, visibleEnd).orEmpty()

        val kLineTop = 4f
        val kLineChartBottom = kLineBottom - 4f

        GridRenderer.draw(this, visibleData, kLineTop, kLineChartBottom, marginLeft, size.width - marginRight, marketColors)

        CandleRenderer.draw(
            this, visibleData,
            top = kLineTop,
            bottom = kLineChartBottom,
            left = marginLeft,
            candleWidth = viewportState.candleWidth,
            gapWidth = viewportState.gapWidth,
            isStepOne = isStepOne,
            colors = marketColors,
        )

        // overlay indicators
        if (indicators.showMa5 && visibleMa5.isNotEmpty()) {
            MaRenderer.draw(this, visibleMa5, visibleData, marketColors.line1, kLineTop, kLineChartBottom, marginLeft, viewportState.candleWidth, viewportState.gapWidth)
        }
        if (indicators.showMa10 && visibleMa10.isNotEmpty()) {
            MaRenderer.draw(this, visibleMa10, visibleData, marketColors.line2, kLineTop, kLineChartBottom, marginLeft, viewportState.candleWidth, viewportState.gapWidth)
        }
        if (indicators.showMa20 && visibleMa20.isNotEmpty()) {
            MaRenderer.draw(this, visibleMa20, visibleData, marketColors.line3, kLineTop, kLineChartBottom, marginLeft, viewportState.candleWidth, viewportState.gapWidth)
        }
        if (indicators.showBoll && bollResult != null) {
            BollRenderer.draw(this, visibleBollUpper, visibleBollMiddle, visibleBollLower, visibleData, kLineTop, kLineChartBottom, marginLeft, viewportState.candleWidth, viewportState.gapWidth, marketColors)
        }

        if (tradeMarkers.isNotEmpty()) {
            TradeMarkerRenderer.draw(
                this,
                tradeMarkers,
                visibleData,
                visibleStart,
                kLineTop,
                kLineChartBottom,
                marginLeft,
                viewportState.candleWidth,
                viewportState.gapWidth,
                marketColors,
            )
        }

        // drawings on K-line area
        if (drawingState.drawings.isNotEmpty() || drawingState.isDrawing) {
            DrawingRenderer.draw(
                this,
                drawingState.drawings,
                drawingState.getPendingPoints(),
                visibleData,
                visibleStart,
                kLineTop,
                kLineChartBottom,
                marginLeft,
                size.width - marginRight,
                viewportState.candleWidth,
                viewportState.gapWidth,
                marketColors,
            )
        }

        // sub-chart panels
        var panelCursor = kLineBottom

        if (showVolume) {
            val volTop = panelCursor
            val volBottom = panelCursor + subPanelHeight
            drawPanelLabel(this, "VOL", volTop, marginLeft, marketColors.chartText, labelDensity)
            VolumeRenderer.draw(this, visibleData, volTop + 20f, volBottom - 4f, marginLeft, viewportState.candleWidth, viewportState.gapWidth, marketColors)
            panelCursor = volBottom
        }

        if (indicators.showMacd && macdResult != null) {
            val pTop = panelCursor
            val pBottom = panelCursor + subPanelHeight
            drawPanelLabel(this, "MACD(${indicators.macdFast},${indicators.macdSlow},${indicators.macdSignal})", pTop, marginLeft, marketColors.chartText, labelDensity)
            MacdRenderer.draw(this, visibleMacdDif, visibleMacdDea, visibleMacdHistogram, pTop, pBottom, marginLeft, size.width - marginRight, viewportState.candleWidth, viewportState.gapWidth, marketColors)
            panelCursor = pBottom
        }

        if (indicators.showRsi && visibleRsi.isNotEmpty()) {
            val pTop = panelCursor
            val pBottom = panelCursor + subPanelHeight
            drawPanelLabel(this, "RSI(${indicators.rsiPeriod})", pTop, marginLeft, marketColors.chartText, labelDensity)
            RsiRenderer.draw(this, visibleRsi, pTop, pBottom, marginLeft, size.width - marginRight, viewportState.candleWidth, viewportState.gapWidth, marketColors)
            panelCursor = pBottom
        }

        if (indicators.showKdj && kdjResult != null) {
            val pTop = panelCursor
            val pBottom = panelCursor + subPanelHeight
            drawPanelLabel(this, "KDJ(${indicators.kdjN},${indicators.kdjK},${indicators.kdjD})", pTop, marginLeft, marketColors.chartText, labelDensity)
            KdjRenderer.draw(this, visibleK, visibleD, visibleJ, pTop, pBottom, marginLeft, size.width - marginRight, viewportState.candleWidth, viewportState.gapWidth, marketColors)
            panelCursor = pBottom
        }

        drawCrosshair(
            point = crosshairState.point,
            visibleData = visibleData,
            visibleStart = visibleStart,
            chartTop = kLineTop,
            chartBottom = kLineChartBottom,
            chartLeft = marginLeft,
            chartRight = size.width - marginRight,
            candleWidth = viewportState.candleWidth,
            gapWidth = viewportState.gapWidth,
            textColor = marketColors.chartText,
            lineColor = marketColors.warning,
            boxBackground = marketColors.chartBackground,
        )
    }
}

private fun drawPanelLabel(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    label: String,
    top: Float,
    left: Float,
    color: Color,
    density: Float,
) {
    val paint = Paint().apply {
        this.color = color.copy(alpha = 0.85f).toArgb()
        textSize = 11f * density
        isAntiAlias = true
    }
    drawScope.drawContext.canvas.nativeCanvas.drawText(label, left + 4f, top + 14f * density, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshair(
    point: CrosshairPoint?,
    visibleData: List<KLineData>,
    visibleStart: Int,
    chartTop: Float,
    chartBottom: Float,
    chartLeft: Float,
    chartRight: Float,
    candleWidth: Float,
    gapWidth: Float,
    textColor: Color,
    lineColor: Color,
    boxBackground: Color,
) {
    if (point == null || visibleData.isEmpty()) return

    val relIdx = point.index - visibleStart
    if (relIdx !in visibleData.indices) return

    val high = visibleData.maxOf { it.high }
    val low = visibleData.minOf { it.low }
    val range = high - low
    if (range <= 0.0 || !range.isFinite()) return

    val candle = visibleData[relIdx]
    val unit = (chartBottom - chartTop) / range.toFloat()
    val cx = chartLeft + relIdx * (candleWidth + gapWidth) + gapWidth / 2f + candleWidth / 2f
    val priceY = (chartTop + (high - point.price).toFloat() * unit).coerceIn(chartTop, chartBottom)
    val guideColor = lineColor.copy(alpha = 0.72f)

    drawLine(guideColor, Offset(cx, chartTop), Offset(cx, chartBottom), strokeWidth = 1f)
    drawLine(guideColor, Offset(chartLeft, priceY), Offset(chartRight, priceY), strokeWidth = 1f)
    drawCircle(lineColor, radius = 4f, center = Offset(cx, priceY))
    drawCircle(Color.White.copy(alpha = 0.75f), radius = 6f, center = Offset(cx, priceY), style = Stroke(width = 1.2f))

    val native = drawContext.canvas.nativeCanvas
    val info = "O ${candle.open.formatPrice()}  H ${candle.high.formatPrice()}"
    val info2 = "L ${candle.low.formatPrice()}  C ${candle.close.formatPrice()}"
    val date = candle.timestamp.formatChartTime()
    val price = point.price.formatPrice()

    val infoPaint = crosshairInfoPaint(textColor)
    val subPaint = crosshairSubPaint(textColor)
    val infoWidth = maxOf(
        infoPaint.measureText(info),
        infoPaint.measureText(info2),
        subPaint.measureText(date),
    )
    val boxPadding = 10f
    val boxWidth = infoWidth + boxPadding * 2f
    val boxHeight = 66f
    val boxLeft = (chartRight - boxWidth - 8f).coerceAtLeast(chartLeft + 8f)
    val boxTop = chartTop + 8f
    native.drawRoundRect(
        RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight),
        6f,
        6f,
        crosshairBoxPaint(boxBackground),
    )
    native.drawText(info, boxLeft + boxPadding, boxTop + 20f, infoPaint)
    native.drawText(info2, boxLeft + boxPadding, boxTop + 40f, infoPaint)
    native.drawText(date, boxLeft + boxPadding, boxTop + 58f, subPaint)

    val labelPaint = crosshairPricePaint(textColor)
    val labelWidth = labelPaint.measureText(price) + 12f
    val labelHeight = 24f
    val labelLeft = (chartRight - labelWidth).coerceAtLeast(chartLeft)
    val labelTop = (priceY - labelHeight / 2f).coerceIn(chartTop, chartBottom - labelHeight)
    drawRect(
        color = lineColor.copy(alpha = 0.95f),
        topLeft = Offset(labelLeft, labelTop),
        size = androidx.compose.ui.geometry.Size(labelWidth, labelHeight),
    )
    native.drawText(price, labelLeft + labelWidth / 2f, labelTop + 17f, labelPaint)
}

private val chartZone: ZoneId = ZoneId.systemDefault()
private val chartDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun Long.formatChartTime(): String {
    return Instant.ofEpochMilli(this).atZone(chartZone).format(chartDateFormatter)
}

private fun Double.formatPrice(): String = "%.2f".format(this)

private fun crosshairInfoPaint(color: Color): Paint {
    return Paint().apply {
        this.color = color.toArgb()
        textSize = 20f
        isAntiAlias = true
    }
}

private fun crosshairSubPaint(color: Color): Paint {
    return Paint().apply {
        this.color = color.copy(alpha = 0.8f).toArgb()
        textSize = 18f
        isAntiAlias = true
    }
}

private fun crosshairPricePaint(color: Color): Paint {
    return Paint().apply {
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = 18f
        isAntiAlias = true
        isFakeBoldText = true
    }
}

private fun crosshairBoxPaint(chartBackground: Color): Paint {
    return Paint().apply {
        // 用主题图表底色派生，亮色主题下不再是深色脏块（C6：不跨主题写死颜色）
        color = chartBackground.copy(alpha = 0.94f).toArgb()
        isAntiAlias = true
    }
}

private fun <T> List<T>.visibleSlice(start: Int, end: Int): List<T> {
    val safeStart = start.coerceIn(0, size)
    val safeEnd = end.coerceIn(safeStart, size)
    return if (safeStart < safeEnd) subList(safeStart, safeEnd) else emptyList()
}
