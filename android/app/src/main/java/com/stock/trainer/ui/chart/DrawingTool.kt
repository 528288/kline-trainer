package com.stock.trainer.ui.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.stock.trainer.domain.model.DrawingObject

enum class DrawingTool(val label: String) {
    POINTER("指针"),
    HORIZONTAL("水平线"),
    TREND_LINE("直线"),
}

@Stable
class DrawingState(
    val drawings: MutableList<DrawingObject> = mutableStateListOf(),
) {
    var currentTool: DrawingTool by mutableStateOf(DrawingTool.POINTER)
    var currentColor: Int by mutableStateOf(Color(0xFFEF5350).toArgb())

    // pending points for multi-tap tools
    private val pendingPoints = mutableStateListOf<Pair<Int, Double>>()

    val isDrawing: Boolean get() = currentTool != DrawingTool.POINTER

    fun selectTool(tool: DrawingTool) {
        if (currentTool == tool) {
            cancelDrawing()
            return
        }
        currentTool = tool
        pendingPoints.clear()
    }

    fun setColor(color: Color) {
        currentColor = color.toArgb()
    }

    fun getCurrentColor(): Color = Color(currentColor)

    /**
     * Called when user taps the K-line chart area.
     * @return true if a drawing was created/updated
     */
    fun onKLineTap(absoluteIndex: Int, price: Double): Boolean {
        if (currentTool == DrawingTool.POINTER) return false

        when (currentTool) {
            DrawingTool.HORIZONTAL -> {
                drawings.add(
                    DrawingObject.HorizontalLine(price = price, color = currentColor)
                )
                return true
            }
            DrawingTool.TREND_LINE -> {
                pendingPoints.add(absoluteIndex to price)
                if (pendingPoints.size >= 2) {
                    val (i1, p1) = pendingPoints[0]
                    val (i2, p2) = pendingPoints[1]
                    drawings.add(
                        DrawingObject.TrendLine(
                            startIndex = i1, startPrice = p1,
                            endIndex = i2, endPrice = p2,
                            color = currentColor,
                        )
                    )
                    pendingPoints.clear()
                }
                return true
            }
            DrawingTool.POINTER -> return false
        }
    }

    fun undoLast() {
        if (drawings.isNotEmpty()) {
            drawings.removeAt(drawings.lastIndex)
        }
    }

    fun clearAll() {
        drawings.clear()
        pendingPoints.clear()
    }

    fun cancelDrawing() {
        pendingPoints.clear()
        currentTool = DrawingTool.POINTER
    }

    fun getPendingPoints(): List<Pair<Int, Double>> = pendingPoints.toList()

    fun getPendingLabel(): String? = when (currentTool) {
        DrawingTool.HORIZONTAL -> "点击放置水平线"
        DrawingTool.TREND_LINE -> when (pendingPoints.size) {
            0 -> "点击设置起点"
            1 -> "点击设置终点"
            else -> null
        }
        DrawingTool.POINTER -> null
    }
}
