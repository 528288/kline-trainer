package com.stock.trainer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 原子件（pll-ui G7 / S1 / S3：一格三尺寸 · 六态 · 命中区 ≥44dp）
 *
 * 组件只引用语义层（C1），不在使用处算色（C2），不用 alpha 造次要层级（C6）。
 * 层级三档由 MaterialTheme.colorScheme 的 onSurface / onSurfaceVariant / outline 承担。
 */

// ---- 形状令牌 ----
val CardShape = RoundedCornerShape(12.dp)
val ChipShape = RoundedCornerShape(8.dp)

// ---- 间距令牌（4 的倍数；组间距 > 组内间距：L5）----
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

/** 行情数字：等宽数字（tnum），右对齐 */
@Composable
fun priceStyle(
    size: Int = 15,
    bold: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
): TextStyle = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.End,
)

/** 涨跌百分比：带符号，符号本身即为颜色之外的第二种编码（C11） */
fun signedPercent(value: Double): String {
    val percent = value * 100
    return when {
        percent > 0.0001 -> "+${"%.2f".format(percent)}%"
        percent < -0.0001 -> "-${"%.2f".format(-percent)}%"
        else -> "0.00%"
    }
}

/**
 * 分段选择片（原 ModeChip：选中只换文字色，命中高度 36dp）。
 * 态：selected / unselected / pressed(ripple) / disabled。触屏无 hover。
 */
@Composable
fun SegmentedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalMarketColors.current
    val container = if (selected) colors.bullishFill else MaterialTheme.colorScheme.surfaceVariant
    val content = when {
        !enabled -> MaterialTheme.colorScheme.outline
        selected -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 主题缩略色板：底色 / 涨 / 跌 三点 */
@Composable
fun ThemeSwatchDots(
    style: AppThemeStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        themeSwatchColors(style).forEach { color ->
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        }
    }
}
