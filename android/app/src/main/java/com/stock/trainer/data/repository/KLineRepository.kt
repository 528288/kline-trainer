package com.stock.trainer.data.repository

import com.stock.trainer.data.local.KLineDao
import com.stock.trainer.data.local.KLineEntity
import com.stock.trainer.domain.model.KLineData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KLineRepository @Inject constructor(
    private val kLineDao: KLineDao,
) {
    fun getKLineStream(symbol: String, period: String): Flow<List<KLineData>> {
        return kLineDao.getKLineStream(symbol, period).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getKLine(symbol: String, period: String): List<KLineData> {
        return kLineDao.getKLine(symbol, period).map { it.toDomain() }.filter { it.isValid() }
    }

    suspend fun getAvailableSymbols(): List<String> {
        return kLineDao.getAllSymbols()
    }

    suspend fun getAvailablePeriods(): List<String> {
        return kLineDao.getAllPeriods()
    }

    suspend fun getTrainableSymbols(period: String, minCount: Int): List<String> {
        return kLineDao.getSymbolsWithAtLeast(period, minCount)
    }

    suspend fun getMultiPeriodTrainableSymbols(minDayCount: Int): List<String> {
        return kLineDao.getSymbolsWithDayWeekMinute60(minDayCount)
    }

    suspend fun getPeriodsForSymbol(symbol: String): List<String> {
        return kLineDao.getPeriodsForSymbol(symbol)
    }

    suspend fun getKLineCount(symbol: String, period: String): Int {
        return kLineDao.count(symbol, period)
    }

    private fun KLineEntity.toDomain() = KLineData(
        timestamp = timestamp,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
    )
}
