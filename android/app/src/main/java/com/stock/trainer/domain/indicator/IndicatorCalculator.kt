package com.stock.trainer.domain.indicator

import com.stock.trainer.domain.model.KLineData
import kotlin.math.sqrt

object IndicatorCalculator {

    fun computeMa(data: List<KLineData>, period: Int): List<Double?> {
        return data.mapIndexed { i, _ ->
            if (i < period - 1) null
            else data.subList(i - period + 1, i + 1).map { it.close }.average()
        }
    }

    fun computeEma(data: List<KLineData>, period: Int): List<Double?> {
        val result = mutableListOf<Double?>()
        var ema = 0.0
        val safePeriod = period.coerceAtLeast(1)
        val multiplier = 2.0 / (safePeriod + 1)
        for (i in data.indices) {
            ema = if (i == 0) {
                data[i].close
            } else {
                (data[i].close - ema) * multiplier + ema
            }
            result.add(ema)
        }
        return result
    }

    fun computeMacd(
        data: List<KLineData>,
        fast: Int = 12,
        slow: Int = 26,
        signal: Int = 9,
    ): MacdResult {
        val emaFast = computeEma(data, fast)
        val emaSlow = computeEma(data, slow)
        val dif = emaFast.zip(emaSlow).map { (f, s) ->
            if (f != null && s != null) f - s else null
        }

        val dea = mutableListOf<Double?>()
        var deaEma = 0.0
        val multiplier = 2.0 / (signal.coerceAtLeast(1) + 1)
        dif.forEachIndexed { i, d ->
            val value = d ?: 0.0
            deaEma = if (i == 0) value else (value - deaEma) * multiplier + deaEma
            dea.add(deaEma)
        }

        val histogram = dif.zip(dea).map { (d, a) ->
            if (d != null && a != null) (d - a) * 2 else null
        }

        return MacdResult(dif, dea, histogram)
    }

    fun computeRsi(data: List<KLineData>, period: Int = 14): List<Double?> {
        val result = mutableListOf<Double?>()
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()

        for (i in data.indices) {
            if (i == 0) {
                result.add(null)
                continue
            }
            val change = data[i].close - data[i - 1].close
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)

            if (i < period) {
                result.add(null)
            } else if (i == period) {
                val avgGain = gains.takeLast(period).average()
                val avgLoss = losses.takeLast(period).average()
                val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
                result.add(100.0 - 100.0 / (1.0 + rs))
            } else {
                val avgGain = gains.takeLast(period).sum() / period
                val avgLoss = losses.takeLast(period).sum() / period
                val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
                result.add(100.0 - 100.0 / (1.0 + rs))
            }
        }
        return result
    }

    fun computeKdj(
        data: List<KLineData>,
        n: Int = 9,
        kPeriod: Int = 3,
        dPeriod: Int = 3,
    ): KdjResult {
        val k = mutableListOf<Double?>()
        val d = mutableListOf<Double?>()
        val j = mutableListOf<Double?>()

        var prevK = 50.0
        var prevD = 50.0

        for (i in data.indices) {
            if (i < n - 1) {
                k.add(null)
                d.add(null)
                j.add(null)
                continue
            }
            val slice = data.subList(i - n + 1, i + 1)
            val high = slice.maxOf { it.high }
            val low = slice.minOf { it.low }
            val rsv = if (high == low) 50.0 else (data[i].close - low) / (high - low) * 100.0

            val newK = (prevK * (kPeriod - 1) + rsv) / kPeriod
            val newD = (prevD * (dPeriod - 1) + newK) / dPeriod
            val newJ = 3 * newK - 2 * newD

            k.add(newK)
            d.add(newD)
            j.add(newJ)

            prevK = newK
            prevD = newD
        }

        return KdjResult(k, d, j)
    }

    fun computeBoll(
        data: List<KLineData>,
        period: Int = 20,
        multiplier: Double = 2.0,
    ): BollResult {
        val middle = computeMa(data, period)
        val upper = mutableListOf<Double?>()
        val lower = mutableListOf<Double?>()

        for (i in data.indices) {
            val mid = middle[i]
            if (mid == null || i < period - 1) {
                upper.add(null)
                lower.add(null)
            } else {
                val slice = data.subList(i - period + 1, i + 1).map { it.close }
                val std = stdDev(slice, mid)
                upper.add(mid + multiplier * std)
                lower.add(mid - multiplier * std)
            }
        }

        return BollResult(upper, middle, lower)
    }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { (it - mean) * (it - mean) }.sum() / (values.size - 1)
        return sqrt(variance)
    }
}

data class MacdResult(val dif: List<Double?>, val dea: List<Double?>, val histogram: List<Double?>)
data class KdjResult(val k: List<Double?>, val d: List<Double?>, val j: List<Double?>)
data class BollResult(val upper: List<Double?>, val middle: List<Double?>, val lower: List<Double?>)
