package com.stock.trainer.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stock.trainer.data.repository.KLineRepository
import com.stock.trainer.data.repository.TrainingRepository
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.domain.model.TradeRecord
import com.stock.trainer.domain.model.TrainingSession
import com.stock.trainer.ui.chart.TradeMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainingRepository: TrainingRepository,
    private val kLineRepository: KLineRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _uiState = MutableStateFlow(SessionDetailUiState(isLoading = true))
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val session = trainingRepository.getSession(sessionId)
                if (session == null) {
                    _uiState.value = SessionDetailUiState(error = "未找到历史对局")
                    return@launch
                }

                val trades = trainingRepository.getTradesBySession(session.id)
                val allData = kLineRepository.getKLine(session.symbol, session.period)
                if (allData.isEmpty()) {
                    _uiState.value = SessionDetailUiState(session = session, trades = trades, error = "未找到K线数据")
                    return@launch
                }

                val inferredSegmentStart = trades
                    .mapNotNull { trade ->
                        val absolute = allData.indexOfTimestamp(trade.entryTime)
                        val relative = trade.indexFromNote("entryIndex")
                        if (absolute != null && relative != null) absolute - relative else null
                    }
                    .minOrNull()
                val tradeStart = trades.mapNotNull { allData.indexOfTimestamp(it.entryTime) }.minOrNull()
                val displayStart = listOfNotNull(session.startIndex, inferredSegmentStart, tradeStart).min()
                val safeStart = displayStart.coerceIn(0, allData.lastIndex)
                val safeEnd = session.endIndex.coerceIn(safeStart, allData.lastIndex)
                val chartData = allData.subList(safeStart, safeEnd + 1)
                val markers = trades.toMarkers(allData, safeStart, safeEnd)

                _uiState.value = SessionDetailUiState(
                    session = session,
                    trades = trades,
                    chartData = chartData,
                    baseIndex = safeStart,
                    markers = markers,
                )
            } catch (e: Exception) {
                _uiState.value = SessionDetailUiState(error = e.message ?: "加载历史对局失败")
            }
        }
    }
}

data class SessionDetailUiState(
    val isLoading: Boolean = false,
    val session: TrainingSession? = null,
    val trades: List<TradeRecord> = emptyList(),
    val chartData: List<KLineData> = emptyList(),
    val baseIndex: Int = 0,
    val markers: List<TradeMarker> = emptyList(),
    val error: String = "",
)

private fun List<TradeRecord>.toMarkers(
    allData: List<KLineData>,
    startIndex: Int,
    endIndex: Int,
): List<TradeMarker> {
    if (allData.isEmpty()) return emptyList()

    return flatMap { trade ->
        val entryIndex = trade.indexFromNote("entryIndex")
            ?.let { startIndex + it }
            ?: allData.indexOfTimestamp(trade.entryTime)
        val exitIndex = trade.indexFromNote("exitIndex")
            ?.let { startIndex + it }
            ?: allData.indexOfTimestamp(trade.exitTime)
        listOfNotNull(
            entryIndex?.let { absolute ->
                val relative = absolute - startIndex
                if (absolute in startIndex..endIndex) TradeMarker(relative, trade.entryPrice, TradeMarker.Side.BUY) else null
            },
            exitIndex?.let { absolute ->
                val relative = absolute - startIndex
                if (absolute in startIndex..endIndex) TradeMarker(relative, trade.exitPrice, TradeMarker.Side.SELL) else null
            },
        )
    }
}

internal fun TradeRecord.indexFromNote(key: String): Int? {
    return note.split(';')
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
}

private fun List<KLineData>.indexOfTimestamp(timestamp: Long): Int? {
    val index = indexOfFirst { it.timestamp == timestamp }
    return index.takeIf { it >= 0 }
}
