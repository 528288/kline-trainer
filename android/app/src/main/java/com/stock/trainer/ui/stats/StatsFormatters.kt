package com.stock.trainer.ui.stats

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val historyZone: ZoneId = ZoneId.of("Asia/Shanghai")

internal fun formatTime(time: Long): String {
    return Instant.ofEpochMilli(time).atZone(historyZone).format(historyFormatter)
}

internal fun formatPnlPercent(value: Double): String {
    val percent = value * 100
    return if (percent >= 0) "+${"%.2f".format(percent)}%" else "%.2f%%".format(percent)
}
