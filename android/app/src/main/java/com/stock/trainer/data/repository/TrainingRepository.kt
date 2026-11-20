package com.stock.trainer.data.repository

import com.stock.trainer.data.local.TradeRecordDao
import com.stock.trainer.data.local.TradeRecordEntity
import com.stock.trainer.data.local.TrainingSessionDao
import com.stock.trainer.data.local.TrainingSessionEntity
import com.stock.trainer.domain.model.TradeRecord
import com.stock.trainer.domain.model.TrainingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingRepository @Inject constructor(
    private val trainingSessionDao: TrainingSessionDao,
    private val tradeRecordDao: TradeRecordDao,
) {
    fun getAllSessionsStream(): Flow<List<TrainingSession>> {
        return trainingSessionDao.getAllStream().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllTradesStream(): Flow<List<TradeRecord>> {
        return tradeRecordDao.getAllStream().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSession(sessionId: String): TrainingSession? {
        return trainingSessionDao.getById(sessionId)?.toDomain()
    }

    suspend fun getTradesBySession(sessionId: String): List<TradeRecord> {
        return tradeRecordDao.getBySession(sessionId)
            .map { it.toDomain() }
            .sortedBy { it.exitTime }
    }

    suspend fun createSession(
        symbol: String,
        period: String,
        startIndex: Int,
        endIndex: Int,
        initialBalance: Double = 1.0,
    ): TrainingSession {
        val session = TrainingSession(
            id = UUID.randomUUID().toString(),
            symbol = symbol,
            period = period,
            startIndex = startIndex,
            endIndex = endIndex,
            startedAt = System.currentTimeMillis(),
            initialBalance = initialBalance,
        )
        trainingSessionDao.insert(session.toEntity())
        return session
    }

    suspend fun getTrainedSymbols(period: String): Set<String> {
        return trainingSessionDao.getTrainedSymbols(period).toSet()
    }

    suspend fun finishSession(
        sessionId: String,
        finalBalance: Double,
        totalTrades: Int,
        winRate: Double,
        totalPnl: Double,
    ) {
        trainingSessionDao.finishSession(
            id = sessionId,
            finishedAt = System.currentTimeMillis(),
            finalBalance = finalBalance,
            totalTrades = totalTrades,
            winRate = winRate,
            totalPnl = totalPnl,
        )
    }

    suspend fun recordTrade(trade: TradeRecord) {
        tradeRecordDao.insert(trade.toEntity())
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        tradeRecordDao.deleteBySession(sessionId)
        trainingSessionDao.deleteById(sessionId)
    }

    suspend fun getStats(): TrainingStats {
        val totalTrades = tradeRecordDao.totalCount()
        val winCount = tradeRecordDao.winCount()
        val totalPnl = tradeRecordDao.totalPnl() ?: 0.0
        return TrainingStats(
            totalTrades = totalTrades,
            winCount = winCount,
            winRate = if (totalTrades > 0) winCount.toDouble() / totalTrades else 0.0,
            totalPnl = totalPnl,
        )
    }
}

data class TrainingStats(
    val totalTrades: Int,
    val winCount: Int,
    val winRate: Double,
    val totalPnl: Double,
)

private fun TrainingSession.toEntity() = TrainingSessionEntity(
    id = id, symbol = symbol, period = period,
    startIndex = startIndex, endIndex = endIndex,
    speed = speed, startedAt = startedAt,
    finishedAt = finishedAt, initialBalance = initialBalance,
    finalBalance = finalBalance, totalTrades = totalTrades,
    winRate = winRate, totalPnl = totalPnl,
)

private fun TrainingSessionEntity.toDomain() = TrainingSession(
    id = id, symbol = symbol, period = period,
    startIndex = startIndex, endIndex = endIndex,
    speed = speed, startedAt = startedAt,
    finishedAt = finishedAt, initialBalance = normalizedInitialBalance(),
    finalBalance = normalizedFinalBalance(), totalTrades = totalTrades,
    winRate = winRate, totalPnl = normalizedTotalPnl(),
)

private fun TradeRecord.toEntity() = TradeRecordEntity(
    id = id, sessionId = sessionId,
    direction = direction.name,
    entryPrice = entryPrice, exitPrice = exitPrice,
    entryTime = entryTime, exitTime = exitTime,
    quantity = quantity, pnl = pnl,
    pnlPercent = pnlPercent, note = note,
)

private fun TradeRecordEntity.toDomain() = TradeRecord(
    id = id, sessionId = sessionId,
    direction = TradeRecord.Direction.valueOf(direction),
    entryPrice = entryPrice, exitPrice = exitPrice,
    entryTime = entryTime, exitTime = exitTime,
    quantity = quantity, pnl = normalizedPnl(),
    pnlPercent = pnlPercent, note = note,
)

private fun TrainingSessionEntity.normalizedInitialBalance(): Double {
    return if (initialBalance > 10.0) 1.0 else initialBalance
}

private fun TrainingSessionEntity.normalizedFinalBalance(): Double? {
    val value = finalBalance ?: return null
    return if (initialBalance > 10.0) value / initialBalance else value
}

private fun TrainingSessionEntity.normalizedTotalPnl(): Double {
    return if (initialBalance > 10.0) totalPnl / initialBalance else totalPnl
}

private fun TradeRecordEntity.normalizedPnl(): Double {
    return if (quantity > 1) pnlPercent else pnl
}
