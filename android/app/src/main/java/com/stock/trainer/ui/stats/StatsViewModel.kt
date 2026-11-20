package com.stock.trainer.ui.stats

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stock.trainer.data.repository.TrainingRepository
import com.stock.trainer.data.repository.TrainingStats
import com.stock.trainer.domain.model.TrainingSession
import com.stock.trainer.domain.model.TradeRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val trainingRepository: TrainingRepository,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS_TRAINING_SETTINGS, Context.MODE_PRIVATE)
    private val _privacySettings = MutableStateFlow(readPrivacySettings())
    val privacySettings: StateFlow<StatsPrivacySettings> = _privacySettings.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_HIDE_SYMBOL || key == KEY_HIDE_TIME) {
            _privacySettings.value = readPrivacySettings()
        }
    }

    private val _expandedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedSessionIds: StateFlow<Set<String>> = _expandedSessionIds.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    val sessions: StateFlow<List<SessionWithTrades>> = combine(
        trainingRepository.getAllSessionsStream(),
        trainingRepository.getAllTradesStream(),
    ) { sessions, trades ->
        val tradesBySession = trades.groupBy { it.sessionId }
        sessions.map { session ->
            SessionWithTrades(
                session = session,
                trades = tradesBySession[session.id].orEmpty().sortedBy { it.exitTime },
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val stats: StateFlow<TrainingStats> = trainingRepository.getAllTradesStream()
        .map { trades ->
            val total = trades.size
            val wins = trades.count { it.pnl > 0 }
            TrainingStats(
                totalTrades = total,
                winCount = wins,
                winRate = if (total > 0) wins.toDouble() / total else 0.0,
                totalPnl = trades.sumOf { it.pnl },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrainingStats(0, 0, 0.0, 0.0),
        )

    fun toggleSession(sessionId: String) {
        _expandedSessionIds.value = _expandedSessionIds.value.toMutableSet().apply {
            if (!add(sessionId)) remove(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            trainingRepository.deleteSession(sessionId)
            _expandedSessionIds.value = _expandedSessionIds.value - sessionId
        }
    }

    private fun readPrivacySettings(): StatsPrivacySettings {
        return StatsPrivacySettings(
            hideSymbol = prefs.getBoolean(KEY_HIDE_SYMBOL, false),
            hideTime = prefs.getBoolean(KEY_HIDE_TIME, false),
        )
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onCleared()
    }

    private companion object {
        const val PREFS_TRAINING_SETTINGS = "training_settings"
        const val KEY_HIDE_SYMBOL = "hide_symbol"
        const val KEY_HIDE_TIME = "hide_time"
    }
}

data class SessionWithTrades(
    val session: TrainingSession,
    val trades: List<TradeRecord>,
)

data class StatsPrivacySettings(
    val hideSymbol: Boolean = false,
    val hideTime: Boolean = false,
)
