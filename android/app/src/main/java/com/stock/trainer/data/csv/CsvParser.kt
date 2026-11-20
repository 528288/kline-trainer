package com.stock.trainer.data.csv

import com.stock.trainer.data.local.KLineEntity
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvParser {

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmm"),
    )

    fun parse(symbol: String, period: String, file: File): List<KLineEntity> {
        val lines = file.readLines()
        if (lines.isEmpty()) return emptyList()

        val dataLines = if (isHeader(lines[0])) lines.drop(1) else lines

        return dataLines.mapNotNull { line ->
            parseLine(symbol, period, line)
        }.sortedBy { it.timestamp }
    }

    fun parseLines(symbol: String, period: String, lines: Sequence<String>): Sequence<KLineEntity> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptySequence()

        val first = iterator.next()
        val dataLines = if (isHeader(first)) iterator.asSequence() else sequenceOf(first) + iterator.asSequence()

        return dataLines.mapNotNull { line ->
            parseLine(symbol, period, line)
        }
    }

    private fun parseLine(symbol: String, period: String, line: String): KLineEntity? {
        val parts = line.trim().split(",")
        if (parts.size < 5) return null

        return try {
            val timestamp = parseTimestamp(parts[0], period)
            if (timestamp == 0L) return null
            val open = parts[1].toDouble()
            val high = parts[2].toDouble()
            val low = parts[3].toDouble()
            val close = parts[4].toDouble()
            if (open.isNaN() || high.isNaN() || low.isNaN() || close.isNaN() ||
                open.isInfinite() || high.isInfinite() || low.isInfinite() || close.isInfinite()) {
                return null
            }
            KLineEntity(
                symbol = symbol,
                period = period,
                timestamp = timestamp,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTimestamp(dateStr: String, period: String): Long {
        val dateTime = dateFormatters.firstNotNullOfOrNull { fmt ->
            try {
                // try as LocalDateTime first (for intraday data like 60min)
                if (dateStr.contains(" ")) {
                    LocalDateTime.parse(dateStr, fmt)
                } else if (dateStr.length == 8 && dateStr.all { it.isDigit() }) {
                    // yyyyMMdd or yyyyMMddHHmm
                    if (dateStr.length == 8) {
                        LocalDateTime.of(
                            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")),
                            LocalTime.of(9, 30),
                        )
                    } else {
                        LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                    }
                } else {
                    val localDate = LocalDate.parse(dateStr, fmt)
                    LocalDateTime.of(localDate, LocalTime.of(9, 30))
                }
            } catch (_: Exception) {
                null
            }
        } ?: return 0L

        val instant = try {
            dateTime.atZone(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {
            dateTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant()
        }
        return instant.toEpochMilli()
    }

    private fun isHeader(firstLine: String): Boolean {
        val lower = firstLine.lowercase()
        return lower.contains("date") || lower.contains("open") || lower.contains("time")
    }
}