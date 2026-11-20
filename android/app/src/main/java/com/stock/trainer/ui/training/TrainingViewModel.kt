package com.stock.trainer.ui.training

import android.content.Context
import com.stock.trainer.data.csv.DataInitializer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stock.trainer.data.local.VipManager
import com.stock.trainer.data.repository.KLineRepository
import com.stock.trainer.data.repository.TrainingRepository
import com.stock.trainer.domain.engine.AdvanceMode
import com.stock.trainer.domain.engine.PlayMode
import com.stock.trainer.domain.engine.TrainingEngine
import com.stock.trainer.domain.engine.TrainingResult
import com.stock.trainer.domain.engine.TrainingUiState
import com.stock.trainer.domain.align.PeriodAligner
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.ui.chart.DrawingState
import com.stock.trainer.ui.chart.IndicatorDisplay
import com.stock.trainer.ui.theme.AppThemeStyle
import com.stock.trainer.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val dataInitializer: DataInitializer,
    private val kLineRepository: KLineRepository,
    private val trainingRepository: TrainingRepository,
    val trainingEngine: TrainingEngine,
    val vipManager: VipManager,
    val themePreferences: ThemePreferences,
) : ViewModel() {

    val engineState: StateFlow<TrainingUiState> = trainingEngine.state

    private val prefs = context.getSharedPreferences("training_settings", Context.MODE_PRIVATE)

    private val _symbol = MutableStateFlow("")
    private val _period = MutableStateFlow("day")
    val symbol: StateFlow<String> = _symbol.asStateFlow()
    val period: StateFlow<String> = _period.asStateFlow()

    private val _multiPeriodKLine = MutableStateFlow(MultiPeriodKLine())
    val multiPeriodKLine: StateFlow<MultiPeriodKLine> = _multiPeriodKLine.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TrainingSettings> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    val indicatorDisplay = IndicatorDisplay()
    private val periodDrawingStates = TrainingChartPeriod.entries.associateWith { DrawingState() }

    private val _trainingResult = MutableStateFlow<TrainingResult?>(null)
    val trainingResult: StateFlow<TrainingResult?> = _trainingResult.asStateFlow()

    fun updateSettings(settings: TrainingSettings) {
        val normalized = settings.normalized()
        _settings.value = normalized
        prefs.edit()
            .putInt(KEY_DISPLAY_COUNT, normalized.displayCount)
            .putInt(KEY_TRAIN_COUNT, normalized.trainCount)
            .putInt(KEY_VISIBLE_COUNT, normalized.visibleCount)
            .putBoolean(KEY_HIDE_SYMBOL, normalized.hideSymbol)
            .putBoolean(KEY_HIDE_TIME, normalized.hideTime)
            .apply()
    }

    fun updateThemeStyle(style: AppThemeStyle) {
        themePreferences.setStyle(style)
    }

    fun startTraining() {
        if (!vipManager.canTrain()) {
            _showVipDialog.value = true
            return
        }
        stopAutoPlay()
        _isLoading.value = true
        _message.value = ""
        viewModelScope.launch {
            try {
                val currentSettings = _settings.value.normalized()
                val per = PERIOD_DAY
                val totalBars = currentSettings.totalBars
                dataInitializer.importSomeForTraining(totalBars)
                var candidates = kLineRepository.getMultiPeriodTrainableSymbols(totalBars)
                if (candidates.size < MIN_READY_TRAINING_SYMBOLS) {
                    _message.value = "正在补充导入训练数据..."
                    dataInitializer.importSomeForTraining(totalBars, MIN_READY_TRAINING_SYMBOLS)
                    candidates = kLineRepository.getMultiPeriodTrainableSymbols(totalBars)
                }
                if (candidates.isEmpty()) {
                    _message.value = "没有同时满足日K${totalBars}根、周K和60分钟K要求的数据"
                    return@launch
                }

                val trainedSymbols = trainingRepository.getTrainedSymbols(per)
                val untrained = candidates.filterNot { it in trainedSymbols }
                val sym = (if (untrained.isNotEmpty()) untrained else candidates).random()
                val allData = kLineRepository.getKLine(sym, PERIOD_DAY)
                val weekData = kLineRepository.getKLine(sym, PERIOD_WEEK)
                val minute60Data = kLineRepository.getKLine(sym, PERIOD_60_MIN)
                if (allData.size < totalBars) {
                    _message.value = "$sym 日K数据不足"
                    return@launch
                }
                if (weekData.isEmpty() || minute60Data.isEmpty()) {
                    _message.value = "$sym 缺少周K或60分钟K数据"
                    return@launch
                }

                // 三周期对齐：把训练段裁到 60 分钟真正覆盖的交易日区间，
                // 否则约 2/3 的随机起点会落在分钟数据之外，切到 60 分钟只会看到无关历史。
                //
                // 重要：60 分钟只覆盖最近约 243 个交易日，而默认训练段要 300 根。
                // 直接按 300 根去对齐会让 99.9% 的股票入选失败（报"60分钟覆盖不足"）。
                // 正确做法是**按实际覆盖能力收缩训练段**，而不是丢弃股票：
                // 展示根数保持用户设定，训练根数取"覆盖上限 - 展示根数"。
                val coverageCap = PeriodAligner.coveredBarCount(allData, minute60Data)
                if (coverageCap <= MIN_TRAIN_BARS) {
                    _message.value = "$sym 的60分钟数据只覆盖${coverageCap}个交易日，无法训练，已换股"
                    return@launch
                }
                val effDisplay = currentSettings.displayCount.coerceAtMost(coverageCap - MIN_TRAIN_BARS)
                val effTrain = currentSettings.trainCount.coerceAtMost(coverageCap - effDisplay)
                val effTotalBars = effDisplay + effTrain

                val aligned = PeriodAligner.slice(
                    day = allData,
                    week = weekData,
                    minute60 = minute60Data,
                    totalBars = effTotalBars,
                    anchorLastDay = true,
                ) ?: run {
                    _message.value = "$sym 的60分钟数据覆盖不足${effTotalBars}根日K，已换股"
                    return@launch
                }
                val segment = aligned.day
                // trainCount 由 engine 派生（allKLineData.size - trainingStartIndex），无需 setter
                val trainingStartIndex = effDisplay
                val shrunkNote = if (effTotalBars < totalBars) {
                    "（60分钟数据只覆盖${coverageCap}个交易日，本次训练段收缩为 展示${effDisplay}+训练${effTrain}）"
                } else ""

                val session = trainingRepository.createSession(
                    symbol = sym,
                    period = per,
                    startIndex = 0,
                    endIndex = segment.size - 1,
                )

                _symbol.value = sym
                _period.value = PERIOD_DAY
                _multiPeriodKLine.value = MultiPeriodKLine(
                    day = segment,
                    week = aligned.week,
                    minute60 = aligned.minute60,
                )
                trainingEngine.initialize(
                    kLineData = segment,
                    sessionId = session.id,
                    trainingStartIndex = trainingStartIndex,
                )
                _trainingResult.value = null
                if (engineState.value.playMode == PlayMode.AUTO) {
                    startAutoPlay()
                }
                _message.value = (if (sym in trainedSymbols) {
                    "未训练数据已用完，随机复训 $sym"
                } else {
                    "开始训练 $sym"
                }) + shrunkNote

                if (!vipManager.isVip) {
                    vipManager.incrementTrainingCount()
                }
            } catch (e: Exception) {
                _message.value = "开始训练失败: ${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _showVipDialog = MutableStateFlow(false)
    val showVipDialog: StateFlow<Boolean> = _showVipDialog.asStateFlow()

    fun openVipDialog() {
        _showVipDialog.value = true
    }

    fun dismissVipDialog() {
        _showVipDialog.value = false
    }

    fun setPlayMode(mode: PlayMode) {
        stopAutoPlay()
        trainingEngine.setPlayMode(mode)
        if (mode == PlayMode.AUTO) {
            startAutoPlay()
        }
    }

    fun setAdvanceMode(mode: AdvanceMode) {
        trainingEngine.setAdvanceMode(mode)
    }

    fun setAutoInterval(ms: Long) = trainingEngine.setAutoInterval(ms)

    private var autoPlayJob: Job? = null

    fun toggleAutoPlay() {
        if (autoPlayJob?.isActive == true) {
            stopAutoPlay()
        } else {
            startAutoPlay()
        }
    }

    private fun startAutoPlay() {
        val interval = engineState.value.autoIntervalMs
        trainingEngine.startAutoPlay()
        autoPlayJob = viewModelScope.launch {
            while (isActive) {
                delay(interval)
                trainingEngine.watch()
                if (engineState.value.isFinished) {
                    stopAutoPlay()
                    finishTraining()
                    break
                }
            }
        }
    }

    fun stopAutoPlay() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        trainingEngine.stopAutoPlay()
    }

    fun watch() = trainingEngine.watch()
    fun twoStepBuy() = trainingEngine.twoStepBuy()
    fun twoStepSell() = trainingEngine.twoStepSell()
    fun bidBuy() = trainingEngine.auctionBuy()
    fun bidSell() = trainingEngine.auctionSell()
    fun closeBuy() = trainingEngine.closeBuy()
    fun closeSell() = trainingEngine.closeSell()

    fun finishTraining() {
        trainingEngine.finishAtCurrentPrice()
        val result = trainingEngine.getTrainingResult()
        _trainingResult.value = result
        viewModelScope.launch {
            trainingRepository.finishSession(
                sessionId = result.sessionId,
                finalBalance = result.finalBalance,
                totalTrades = result.totalTrades,
                winRate = result.winRate,
                totalPnl = result.totalPnl,
            )
            result.trades.forEach { trade ->
                trainingRepository.recordTrade(trade)
            }
        }
    }

    fun resetTraining() {
        stopAutoPlay()
        trainingEngine.reset()
        _symbol.value = ""
        _multiPeriodKLine.value = MultiPeriodKLine()
        _trainingResult.value = null
    }

    fun getVisibleKLine(period: TrainingChartPeriod, state: TrainingUiState = engineState.value): List<KLineData> {
        return when (period) {
            TrainingChartPeriod.DAY -> trainingEngine.getVisibleKLine()
            TrainingChartPeriod.WEEK -> _multiPeriodKLine.value.week.visibleUntil(state.currentDailyCutoffMillis())
            TrainingChartPeriod.MINUTE_60 -> _multiPeriodKLine.value.minute60.visibleUntil(state.currentDailyCutoffMillis())
        }
    }

    fun getDrawingState(period: TrainingChartPeriod): DrawingState {
        return periodDrawingStates.getValue(period)
    }

    private fun loadSettings(): TrainingSettings {
        return TrainingSettings(
            displayCount = prefs.getInt(KEY_DISPLAY_COUNT, 150),
            trainCount = prefs.getInt(KEY_TRAIN_COUNT, 150),
            visibleCount = prefs.getInt(KEY_VISIBLE_COUNT, 60),
            hideSymbol = prefs.getBoolean(KEY_HIDE_SYMBOL, false),
            hideTime = prefs.getBoolean(KEY_HIDE_TIME, false),
        ).normalized()
    }

    companion object {
        private const val KEY_DISPLAY_COUNT = "display_count"
        private const val KEY_TRAIN_COUNT = "train_count"
        private const val KEY_VISIBLE_COUNT = "visible_count"
        private const val KEY_HIDE_SYMBOL = "hide_symbol"
        private const val KEY_HIDE_TIME = "hide_time"
        private const val PERIOD_DAY = "day"
        private const val PERIOD_WEEK = "week"
        private const val PERIOD_60_MIN = "60min"
        private const val MIN_READY_TRAINING_SYMBOLS = 20
        private const val MIN_TRAIN_BARS = 20
    }
}

data class MultiPeriodKLine(
    val day: List<KLineData> = emptyList(),
    val week: List<KLineData> = emptyList(),
    val minute60: List<KLineData> = emptyList(),
)

enum class TrainingChartPeriod(val label: String) {
    DAY("日K"),
    WEEK("周K"),
    MINUTE_60("60分钟"),
}

data class TrainingSettings(
    val displayCount: Int = 150,
    val trainCount: Int = 150,
    val visibleCount: Int = 60,
    val hideSymbol: Boolean = false,
    val hideTime: Boolean = false,
) {
    val totalBars: Int get() = displayCount + trainCount

    fun normalized(): TrainingSettings {
        return copy(
            displayCount = displayCount.coerceIn(10, 1000),
            trainCount = trainCount.coerceIn(1, 1000),
            visibleCount = visibleCount.coerceIn(10, 200),
        )
    }
}

private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

private fun TrainingUiState.currentDailyCutoffMillis(): Long? {
    if (currentIndex !in allKLineData.indices) return null

    val indexForCompletedDaily = if (advanceMode == AdvanceMode.TWO_STEP && isStepOne) {
        currentIndex - 1
    } else {
        currentIndex
    }
    if (indexForCompletedDaily !in allKLineData.indices) return null

    val date = allKLineData[indexForCompletedDaily].timestamp.toLocalDate()
    return date.plusDays(1).atStartOfDay(chinaZone).toInstant().toEpochMilli() - 1
}

private fun List<KLineData>.visibleUntil(cutoffMillis: Long?): List<KLineData> {
    if (cutoffMillis == null) return emptyList()
    // endExclusive == -1 表示没有 K 线超过截止时间 → 全部都在范围内，返回全集（不是"没数据"）
    val endExclusive = indexOfFirst { it.timestamp > cutoffMillis }
    return if (endExclusive == -1) this else subList(0, endExclusive)
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(chinaZone).toLocalDate()
}
