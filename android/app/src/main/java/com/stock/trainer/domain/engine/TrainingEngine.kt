package com.stock.trainer.domain.engine

import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.domain.model.TradeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

class TrainingEngine @Inject constructor() {

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    fun initialize(
        kLineData: List<KLineData>,
        sessionId: String,
        trainingStartIndex: Int = 0,
        initialBalance: Double = 1.0,
    ) {
        if (kLineData.isEmpty()) return
        val s = _state.value
        val safeTrainingStart = trainingStartIndex.coerceIn(0, kLineData.lastIndex)
        val firstCandle = kLineData[safeTrainingStart]
        _state.value = TrainingUiState(
            sessionId = sessionId,
            allKLineData = kLineData,
            currentIndex = safeTrainingStart,
            trainingStartIndex = safeTrainingStart,
            visibleStart = 0,
            balance = initialBalance,
            initialBalance = initialBalance,
            visibleCount = minOf(kLineData.size, safeTrainingStart + 1),
            currentPrice = if (s.advanceMode == AdvanceMode.TWO_STEP) firstCandle.open else firstCandle.close,
            playMode = s.playMode,
            advanceMode = s.advanceMode,
            isStepOne = s.advanceMode == AdvanceMode.TWO_STEP,
            autoIntervalMs = s.autoIntervalMs,
        )
    }

    fun setPlayMode(mode: PlayMode) {
        _state.update { s ->
            s.copy(
                playMode = mode,
                isAutoPlaying = if (mode == PlayMode.AUTO) s.isAutoPlaying else false,
            )
        }
    }

    fun setAdvanceMode(mode: AdvanceMode) {
        _state.update { s ->
            val price = s.priceFor(mode, isStepOne = mode == AdvanceMode.TWO_STEP)
            s.copy(
                advanceMode = mode,
                isStepOne = mode == AdvanceMode.TWO_STEP,
                pendingBidOrder = if (mode == AdvanceMode.SINGLE_STEP) s.pendingBidOrder else null,
                currentPrice = price,
            )
        }
    }

    fun setAutoInterval(ms: Long) {
        _state.update { s -> s.copy(autoIntervalMs = ms.coerceIn(500, 10000)) }
    }

    fun watch() {
        _state.update { s -> s.advanceToNextPhase() }
    }

    fun twoStepBuy(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.TWO_STEP) return@update s
            val price = s.currentPrice ?: return@update s
            if (s.position != null) return@update s
            if (s.currentIndex !in s.allKLineData.indices) return@update s
            s.openLongAt(price, s.currentIndex, quantity).advanceToNextPhase()
        }
    }

    fun twoStepSell(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.TWO_STEP) return@update s
            val price = s.currentPrice ?: return@update s
            if (!s.canSellOnCurrentCandle()) return@update s
            s.closeLongAt(price, s.currentIndex, quantity).advanceToNextPhase()
        }
    }

    fun auctionBuy(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.SINGLE_STEP) return@update s
            if (s.position != null) return@update s
            if (!s.hasNextCandle()) return@update s
            s.copy(pendingBidOrder = BidOrder(TradeRecord.Direction.LONG, quantity)).advanceToNextPhase()
        }
    }

    fun auctionSell(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.SINGLE_STEP) return@update s
            if (!s.canSellOnCurrentCandle()) return@update s
            if (!s.hasNextCandle()) return@update s
            s.copy(pendingBidOrder = BidOrder(TradeRecord.Direction.SHORT, quantity)).advanceToNextPhase()
        }
    }

    fun closeBuy(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.SINGLE_STEP) return@update s
            if (s.currentIndex !in s.allKLineData.indices) return@update s
            val price = s.allKLineData[s.currentIndex].close
            if (s.position != null) return@update s
            s.openLongAt(price, s.currentIndex, quantity).advanceToNextPhase()
        }
    }

    fun closeSell(quantity: Int = 100) {
        _state.update { s ->
            if (s.advanceMode != AdvanceMode.SINGLE_STEP) return@update s
            if (s.currentIndex !in s.allKLineData.indices) return@update s
            if (!s.canSellOnCurrentCandle()) return@update s
            s.closeLongAt(s.allKLineData[s.currentIndex].close, s.currentIndex, quantity).advanceToNextPhase()
        }
    }

    fun finishAtCurrentPrice() {
        _state.update { s ->
            val price = s.currentPrice ?: return@update s
            s.closeLongAt(price, s.currentIndex, allowSameBar = true)
        }
    }

    fun skipToEnd() {
        _state.update { s ->
            if (s.allKLineData.isEmpty()) return@update s
            val lastIdx = s.allKLineData.lastIndex
            var result = s
            if (result.position != null) {
                result = result.closeLongAt(s.allKLineData[lastIdx].close, lastIdx)
            }
            result.copy(
                currentIndex = lastIdx,
                visibleStart = maxOf(0, lastIdx - s.visibleCount),
                currentPrice = s.allKLineData[lastIdx].close,
                isStepOne = false,
                pendingBidOrder = null,
            )
        }
    }

    fun getVisibleKLine(): List<KLineData> {
        val s = _state.value
        if (s.allKLineData.isEmpty()) return emptyList()
        val visible = s.allKLineData.subList(0, (s.currentIndex + 1).coerceAtMost(s.allKLineData.size))
        if (visible.isEmpty() || s.advanceMode != AdvanceMode.TWO_STEP || !s.isStepOne) return visible

        val last = visible.last()
        return visible.dropLast(1) + last.copy(
            high = last.open,
            low = last.open,
            close = last.open,
        )
    }

    fun getTrainingResult(): TrainingResult {
        val s = _state.value
        val totalPnl = s.totalPnl
        val winCount = s.trades.count { it.pnl > 0 }
        return TrainingResult(
            sessionId = s.sessionId,
            initialBalance = s.initialBalance,
            finalBalance = s.balance,
            totalPnl = totalPnl,
            totalTrades = s.trades.size,
            winCount = winCount,
            winRate = if (s.trades.isNotEmpty()) winCount.toDouble() / s.trades.size else 0.0,
            trades = s.trades,
        )
    }

    fun startAutoPlay() {
        _state.update { it.copy(playMode = PlayMode.AUTO, isAutoPlaying = true) }
    }

    fun stopAutoPlay() {
        _state.update { it.copy(isAutoPlaying = false) }
    }

    fun reset() {
        _state.value = TrainingUiState()
    }

    private fun TrainingUiState.advanceToNextPhase(): TrainingUiState {
        if (!isStarted || isFinished) return this
        return when (advanceMode) {
            AdvanceMode.TWO_STEP -> {
                if (isStepOne) {
                    copy(
                        isStepOne = false,
                        currentPrice = allKLineData[currentIndex].close,
                    )
                } else {
                    advanceToNextCandle(openOnly = true)
                }
            }

            AdvanceMode.SINGLE_STEP -> advanceToNextCandle(openOnly = false)
        }
    }

    private fun TrainingUiState.advanceToNextCandle(openOnly: Boolean): TrainingUiState {
        val next = currentIndex + 1
        if (next >= allKLineData.size) return this
        val newVisibleStart = if (next >= visibleStart + visibleCount - 5) {
            minOf(visibleStart + 1, next - visibleCount + 5)
        } else visibleStart
        var result = copy(
            currentIndex = next,
            visibleStart = newVisibleStart,
            currentPrice = if (openOnly) allKLineData[next].open else allKLineData[next].close,
            isStepOne = openOnly,
        )
        result = result.executePendingOrderAtOpen()
        return result
    }

    private fun TrainingUiState.executePendingOrderAtOpen(): TrainingUiState {
        val order = pendingBidOrder ?: return this
        if (currentIndex !in allKLineData.indices) return copy(pendingBidOrder = null)
        val price = allKLineData[currentIndex].open
        val result = when (order.direction) {
            TradeRecord.Direction.LONG -> openLongAt(price, currentIndex, order.quantity)
            TradeRecord.Direction.SHORT -> closeLongAt(price, currentIndex, order.quantity)
        }
        return result.copy(pendingBidOrder = null)
    }

    private fun TrainingUiState.priceFor(mode: AdvanceMode, isStepOne: Boolean): Double? {
        if (currentIndex !in allKLineData.indices) return currentPrice
        val candle = allKLineData[currentIndex]
        return if (mode == AdvanceMode.TWO_STEP && isStepOne) candle.open else candle.close
    }

    private fun TrainingUiState.hasNextCandle(): Boolean {
        return currentIndex < allKLineData.lastIndex
    }

    private fun TrainingUiState.canSellOnCurrentCandle(): Boolean {
        val pos = position ?: return false
        return pos.entryIndex < currentIndex
    }

    private fun TrainingUiState.openLongAt(
        price: Double,
        index: Int,
        quantity: Int = 100,
    ): TrainingUiState {
        if (position != null) return this
        if (index !in allKLineData.indices) return this
        if (price <= 0.0 || !price.isFinite()) return this
        return copy(
            position = Position(
                entryPrice = price,
                entryIndex = index,
                entryTime = allKLineData[index].timestamp,
                quantity = 1,
                capitalAtEntry = balance,
            ),
        )
    }

    private fun TrainingUiState.closeLongAt(
        price: Double,
        index: Int,
        quantity: Int = position?.quantity ?: 1,
        allowSameBar: Boolean = false,
    ): TrainingUiState {
        val pos = position ?: return this
        if (index !in allKLineData.indices) return this
        if (!allowSameBar && pos.entryIndex == index) return this
        if (price <= 0.0 || !price.isFinite()) return this
        val exitCapital = pos.capitalAtEntry / pos.entryPrice * price
        val pnl = exitCapital - pos.capitalAtEntry
        val pnlPercent = price / pos.entryPrice - 1.0
        return copy(
            balance = exitCapital,
            position = null,
            trades = trades + TradeRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                direction = TradeRecord.Direction.LONG,
                entryPrice = pos.entryPrice,
                exitPrice = price,
                entryTime = pos.entryTime,
                exitTime = allKLineData[index].timestamp,
                quantity = minOf(quantity, pos.quantity),
                pnl = pnl,
                pnlPercent = pnlPercent,
                note = "entryIndex=${pos.entryIndex};exitIndex=$index",
            ),
        )
    }
}

data class TrainingUiState(
    val sessionId: String = "",
    val allKLineData: List<KLineData> = emptyList(),
    val currentIndex: Int = 0,
    val trainingStartIndex: Int = 0,
    val visibleStart: Int = 0,
    val visibleCount: Int = 60,
    val currentPrice: Double? = null,
    val balance: Double = 1.0,
    val initialBalance: Double = 1.0,
    val position: Position? = null,
    val trades: List<TradeRecord> = emptyList(),
    val playMode: PlayMode = PlayMode.MANUAL,
    val advanceMode: AdvanceMode = AdvanceMode.TWO_STEP,
    val isStepOne: Boolean = false,
    val pendingBidOrder: BidOrder? = null,
    val isAutoPlaying: Boolean = false,
    val autoIntervalMs: Long = 2000,
) {
    val isStarted: Boolean get() = allKLineData.isNotEmpty()
    val trainCount: Int get() = if (allKLineData.isNotEmpty()) {
        allKLineData.size - trainingStartIndex
    } else 0
    val trainedCount: Int get() = if (isStarted) {
        (currentIndex - trainingStartIndex + 1).coerceIn(0, trainCount)
    } else 0
    val isFinished: Boolean get() {
        if (!isStarted || currentIndex < allKLineData.lastIndex) return false
        return advanceMode == AdvanceMode.SINGLE_STEP || !isStepOne
    }
    val progress: Float get() = if (allKLineData.isNotEmpty()) {
        trainedCount.toFloat() / trainCount.coerceAtLeast(1)
    } else 0f

    val unrealizedPnl: Double get() {
        val pos = position ?: return 0.0
        val price = currentPrice ?: return 0.0
        if (pos.entryPrice <= 0.0 || price <= 0.0) return 0.0
        return pos.capitalAtEntry / pos.entryPrice * price - pos.capitalAtEntry
    }

    val totalEquity: Double get() = position?.let { it.capitalAtEntry + unrealizedPnl } ?: balance
    val totalPnl: Double get() = totalEquity - initialBalance
}

data class Position(
    val entryPrice: Double,
    val entryIndex: Int,
    val entryTime: Long,
    val quantity: Int,
    val capitalAtEntry: Double,
)

data class TrainingResult(
    val sessionId: String,
    val initialBalance: Double,
    val finalBalance: Double,
    val totalPnl: Double,
    val totalTrades: Int,
    val winCount: Int,
    val winRate: Double,
    val trades: List<TradeRecord>,
)
