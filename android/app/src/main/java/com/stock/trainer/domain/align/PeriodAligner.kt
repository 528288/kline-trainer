package com.stock.trainer.domain.align

import com.stock.trainer.domain.model.KLineData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 多周期对齐（日K / 周K / 60分钟）。
 *
 * 背景：内置数据的日K有 3200+ 根（约 13 年），而 60 分钟只有最近约 243 个交易日。
 * 如果在全量日K里随机切训练段，约 2/3 的起点落在 60 分钟无数据区间，
 * 切到 60 分钟标签页就只能看到一段与当前训练日毫无关系的历史数据。
 *
 * 解法：把训练用的日K**裁剪到 60 分钟实际覆盖的交易日区间**，三周期严格对齐。
 * 裁剪后每根日K都有对应的 60 分钟明细，分钟级视图不再是"另一段历史"。
 */
object PeriodAligner {

    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 一段连续交易日上的 K 线数据 */
    data class AlignedSeries(
        val day: List<KLineData>,
        val week: List<KLineData>,
        val minute60: List<KLineData>,
    ) {
        val isEmpty: Boolean get() = day.isEmpty() || week.isEmpty() || minute60.isEmpty()
    }

    /** 日K中包含 60 分钟数据的最后一个交易日（yyyy-MM-dd），没有则 null */
    fun lastCoveredDate(day: List<KLineData>, minute60: List<KLineData>): LocalDate? {
        if (day.isEmpty() || minute60.isEmpty()) return null
        val minuteDates = minute60.mapTo(HashSet()) { it.timestamp.toDate() }
        return day.asReversed().firstOrNull { it.timestamp.toDate() in minuteDates }?.timestamp?.toDate()
    }

    /**
     * 60 分钟实际覆盖到的日K根数（= 训练段能取的最大长度）。
     *
     * 内置数据里 60 分钟只有最近约 243 个交易日，日K却有 3200+ 根。
     * 训练段一旦超过这个数，就必然有一部分日K没有分钟明细，
     * 所以训练段长度必须以它为上限收缩，而不是把整只股票丢掉。
     */
    fun coveredBarCount(day: List<KLineData>, minute60: List<KLineData>): Int {
        if (day.isEmpty() || minute60.isEmpty()) return 0
        val minuteDates = minute60.mapTo(HashSet()) { it.timestamp.toDate() }
        return day.count { it.timestamp.toDate() in minuteDates }
    }

    /**
     * 把三周期对齐到 60 分钟覆盖的交易日区间，并切出训练段。
     *
     * @param totalBars 训练段需要的日K根数（展示 + 训练）
     * @param anchorLastDay true = 训练段紧贴 60 分钟覆盖的末日（推荐：保证训练全程分钟数据可用；
     *                      false = 在可用区间内随机）
     */
    fun slice(
        day: List<KLineData>,
        week: List<KLineData>,
        minute60: List<KLineData>,
        totalBars: Int,
        random: kotlin.random.Random = kotlin.random.Random,
        anchorLastDay: Boolean = true,
    ): AlignedSeries? {
        if (totalBars <= 0) return null

        // 1. 60 分钟覆盖的交易日集合
        val minuteDates = minute60.mapTo(HashSet()) { it.timestamp.toDate() }
        if (minuteDates.isEmpty()) return null

        // 2. 只保留 60 分钟覆盖得到的那些日K，且必须是连续可用的
        val coveredDay = day.filter { it.timestamp.toDate() in minuteDates }
        if (coveredDay.size < totalBars) return null

        // 3. 选定连续窗口（coveredDay 已经是 60 分钟覆盖过的交易日，天然连续）
        val maxStart = coveredDay.lastIndex - totalBars + 1
        val startIdx = if (anchorLastDay) maxStart else random.nextInt(maxStart + 1)
        val segment = coveredDay.subList(startIdx, startIdx + totalBars)

        val segStartTs = segment.first().timestamp
        val segEndDate = segment.last().timestamp.toDate()
        // 段末整天都算可见（60 分钟最后一根在该日收盘）
        val segEndExclusive = segEndDate.plusDays(1).atStartOfDay(chinaZone).toInstant().toEpochMilli()

        // 4. 周K：按训练段的时间范围裁（周K用周内最后一根日K的日期归属）
        val segWeek = week.filter { it.timestamp >= segStartTs && it.timestamp < segEndExclusive }

        // 5. 60 分钟：裁到同一区间
        val segMinute60 = minute60.filter { it.timestamp >= segStartTs && it.timestamp < segEndExclusive }

        if (segWeek.isEmpty() || segMinute60.isEmpty()) return null
        return AlignedSeries(day = segment, week = segWeek, minute60 = segMinute60)
    }

    private fun Long.toDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(chinaZone).toLocalDate()
}
