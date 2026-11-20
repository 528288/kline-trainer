package com.stock.trainer.ui.training

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.stock.trainer.domain.engine.AdvanceMode
import com.stock.trainer.domain.engine.PlayMode
import com.stock.trainer.domain.engine.Position
import com.stock.trainer.domain.engine.TrainingResult
import com.stock.trainer.domain.engine.TrainingUiState
import com.stock.trainer.domain.model.KLineData
import com.stock.trainer.domain.model.TradeRecord
import com.stock.trainer.ui.chart.CandlestickChart
import com.stock.trainer.ui.chart.DrawingState
import com.stock.trainer.ui.chart.DrawingTool
import com.stock.trainer.ui.chart.IndicatorDisplay
import com.stock.trainer.ui.chart.TradeMarker
import com.stock.trainer.ui.chart.ViewportState
import com.stock.trainer.ui.theme.AppThemeStyle
import com.stock.trainer.ui.theme.AppThemeStyles
import com.stock.trainer.ui.theme.ChipShape
import com.stock.trainer.ui.theme.LocalMarketColors
import com.stock.trainer.ui.theme.SegmentedChip
import com.stock.trainer.ui.theme.Spacing
import com.stock.trainer.ui.theme.ThemeSwatchDots
import com.stock.trainer.ui.theme.priceStyle
import com.stock.trainer.ui.theme.signedPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel = hiltViewModel(),
) {
    val engineState by viewModel.engineState.collectAsState()
    val symbol by viewModel.symbol.collectAsState()
    val period by viewModel.period.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val result by viewModel.trainingResult.collectAsState()
    val showVipDialog by viewModel.showVipDialog.collectAsState()
    val isVip by viewModel.vipManager.isVipFlow.collectAsState()
    val themeStyle by viewModel.themePreferences.style.collectAsState()
    val marketColors = LocalMarketColors.current
    var showIndicatorDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedChartPeriod by remember { mutableStateOf(TrainingChartPeriod.DAY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (symbol.isBlank()) {
                                "K线训练"
                            } else if (settings.hideSymbol) {
                                "盲测标的 · $period"
                            } else {
                                "$symbol · $period"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (engineState.isStarted) {
                                "展示${engineState.trainingStartIndex}根 · 训练${engineState.trainedCount}/${engineState.trainCount}根 · 屏幕${settings.visibleCount}根"
                            } else {
                                "默认展示${settings.displayCount}根，训练${settings.trainCount}根，屏幕${settings.visibleCount}根"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showIndicatorDialog = true }) {
                        Text("指标", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { viewModel.openVipDialog() }) {
                        Text(
                            "支持",
                            color = if (isVip) marketColors.warning
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            result != null -> {
                TrainingResultView(
                    result = result!!,
                    onNewTraining = { viewModel.startTraining() },
                )
            }

            !engineState.isStarted -> {
                StartTrainingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    isLoading = isLoading,
                    message = message,
                    settings = settings,
                    vipManager = viewModel.vipManager,
                    onStart = { viewModel.startTraining() },
                    onSettings = { showSettingsDialog = true },
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LinearProgressIndicator(
                        progress = { engineState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    StatusBar(
                        symbol = if (settings.hideSymbol) "盲测标的" else symbol,
                        isLoading = isLoading,
                        message = message,
                        engineState = engineState,
                        hideTime = settings.hideTime,
                        onNextRound = { viewModel.finishTraining() },
                    )

                    val drawingState = viewModel.getDrawingState(selectedChartPeriod)
                    DrawingToolbar(drawingState)

                    ChartPeriodTabs(
                        selected = selectedChartPeriod,
                        onSelected = { selectedChartPeriod = it },
                    )

                    val viewportState = remember { ViewportState() }
                    var lastChartPeriod by remember { mutableStateOf(selectedChartPeriod) }
                    var lastVisibleCount by remember { mutableStateOf(settings.visibleCount) }
                    val chartData = viewModel.getVisibleKLine(selectedChartPeriod, engineState)
                    val tradeMarkers = remember(selectedChartPeriod, engineState.trades, engineState.position, chartData) {
                        if (selectedChartPeriod == TrainingChartPeriod.DAY) {
                            buildDailyTradeMarkers(engineState.trades, engineState.position, chartData)
                        } else {
                            emptyList()
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val density = LocalDensity.current
                        val chartWidthPx = with(density) { maxWidth.toPx() }
                        val anchorIndex = if (selectedChartPeriod == TrainingChartPeriod.DAY) {
                            engineState.currentIndex.coerceAtLeast(0)
                        } else {
                            (chartData.size - 1).coerceAtLeast(0)
                        }
                        LaunchedEffect(selectedChartPeriod, chartData.size, anchorIndex, settings.visibleCount, chartWidthPx) {
                            val visibleCount = settings.visibleCount.coerceAtMost(chartData.size.coerceAtLeast(1))
                            val drawableWidth = (chartWidthPx - 72f).coerceAtLeast(visibleCount.toFloat())
                            val itemWidth = drawableWidth / visibleCount
                            viewportState.gapWidth = (itemWidth * 0.22f).coerceIn(1f, 4f)
                            viewportState.candleWidth = (itemWidth - viewportState.gapWidth).coerceAtLeast(2f)
                            val expectedLatestStart = (viewportState.dataSize - viewportState.visibleCount)
                                .coerceAtLeast(0)
                            val shouldAnchorToLatest =
                                    selectedChartPeriod != lastChartPeriod ||
                                    settings.visibleCount != lastVisibleCount ||
                                    viewportState.dataSize == 0 ||
                                    viewportState.visibleStart >= expectedLatestStart

                            if (shouldAnchorToLatest) {
                                viewportState.setWindow(
                                    size = chartData.size,
                                    count = settings.visibleCount,
                                    anchorIndex = anchorIndex,
                                )
                            } else {
                                viewportState.setWindowPreservingStart(
                                    size = chartData.size,
                                    count = settings.visibleCount,
                                )
                            }
                            lastChartPeriod = selectedChartPeriod
                            lastVisibleCount = settings.visibleCount
                        }

                        CandlestickChart(
                            data = chartData,
                            viewportState = viewportState,
                            indicators = viewModel.indicatorDisplay,
                            drawingState = drawingState,
                            tradeMarkers = tradeMarkers,
                            isStepOne = selectedChartPeriod == TrainingChartPeriod.DAY && engineState.isStepOne,
                        )
                        if (chartData.isEmpty()) {
                            Text(
                                selectedChartPeriod.emptyMessage(),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    engineState.position?.let { pos ->
                        PositionInfoBar(
                            position = pos,
                            currentPrice = engineState.currentPrice,
                            unrealizedPnl = engineState.unrealizedPnl,
                        )
                    }

                    if (engineState.trades.isNotEmpty()) {
                        TradeSummaryBar(engineState.trades)
                    }

                    ModeSelector(
                        playMode = engineState.playMode,
                        advanceMode = engineState.advanceMode,
                        onPlayModeChange = { viewModel.setPlayMode(it) },
                        onAdvanceModeChange = { viewModel.setAdvanceMode(it) },
                    )

                    val canBuy = engineState.position == null
                    val canSell = engineState.position?.let { it.entryIndex < engineState.currentIndex } == true
                    val hasNextCandle = engineState.currentIndex < engineState.allKLineData.lastIndex

                    ModeActionBar(
                        playMode = engineState.playMode,
                        advanceMode = engineState.advanceMode,
                        isStepOne = engineState.isStepOne,
                        isAutoPlaying = engineState.isAutoPlaying,
                        autoIntervalMs = engineState.autoIntervalMs,
                        isFinished = engineState.isFinished,
                        canBuy = canBuy,
                        canSell = canSell,
                        hasNextCandle = hasNextCandle,
                        totalPnl = engineState.totalPnl,
                        onWatch = { viewModel.watch() },
                        onTwoStepBuy = { viewModel.twoStepBuy() },
                        onTwoStepSell = { viewModel.twoStepSell() },
                        onBidBuy = { viewModel.bidBuy() },
                        onBidSell = { viewModel.bidSell() },
                        onCloseBuy = { viewModel.closeBuy() },
                        onCloseSell = { viewModel.closeSell() },
                        onToggleAutoPlay = { viewModel.toggleAutoPlay() },
                        onSetAutoInterval = { viewModel.setAutoInterval(it) },
                        onFinish = { viewModel.finishTraining() },
                    )
                }
            }
        }
    }

    if (showIndicatorDialog) {
        IndicatorDialog(
            indicators = viewModel.indicatorDisplay,
            onDismiss = { showIndicatorDialog = false },
        )
    }

    if (showSettingsDialog) {
        TrainingSettingsDialog(
            settings = settings,
            selectedThemeStyle = themeStyle,
            onSave = {
                viewModel.updateSettings(it.settings)
                viewModel.updateThemeStyle(it.themeStyle)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false },
        )
    }

    if (showVipDialog) {
        VipDialog(
            vipManager = viewModel.vipManager,
            onDismiss = { viewModel.dismissVipDialog() },
        )
    }
}

private fun buildDailyTradeMarkers(
    trades: List<TradeRecord>,
    position: Position?,
    data: List<KLineData>,
): List<TradeMarker> {
    if (data.isEmpty() || (trades.isEmpty() && position == null)) return emptyList()

    val markers = mutableListOf<TradeMarker>()
    trades.forEach { trade ->
        val entryIndex = trade.note.tradeIndex("entryIndex") ?: data.indexOfTimestamp(trade.entryTime)
        val exitIndex = trade.note.tradeIndex("exitIndex") ?: data.indexOfTimestamp(trade.exitTime)

        if (entryIndex != null && entryIndex in data.indices) {
            markers += TradeMarker(entryIndex, trade.entryPrice, TradeMarker.Side.BUY)
        }
        if (exitIndex != null && exitIndex in data.indices) {
            markers += TradeMarker(exitIndex, trade.exitPrice, TradeMarker.Side.SELL)
        }
    }

    position?.let {
        val entryIndex = if (it.entryIndex in data.indices) {
            it.entryIndex
        } else {
            data.indexOfTimestamp(it.entryTime)
        }
        if (entryIndex != null && entryIndex in data.indices) {
            markers += TradeMarker(entryIndex, it.entryPrice, TradeMarker.Side.BUY)
        }
    }

    return markers
}

private fun String.tradeIndex(key: String): Int? {
    val keyStart = indexOf("$key=")
    if (keyStart < 0) return null
    val valueStart = keyStart + key.length + 1
    val valueEnd = indexOf(';', valueStart).takeIf { it >= 0 } ?: length
    return substring(valueStart, valueEnd).toIntOrNull()
}

private fun TrainingChartPeriod.emptyMessage(): String {
    return when (this) {
        TrainingChartPeriod.MINUTE_60 -> "该训练日没有对应的60分钟明细数据"
        else -> "${label}暂无可显示数据"
    }
}

private fun List<KLineData>.indexOfTimestamp(timestamp: Long): Int? {
    val index = indexOfFirst { it.timestamp == timestamp }
    return index.takeIf { it >= 0 }
}

@Composable
private fun StartTrainingView(
    modifier: Modifier,
    isLoading: Boolean,
    message: String,
    settings: TrainingSettings,
    vipManager: com.stock.trainer.data.local.VipManager,
    onStart: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("开始训练", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "随机抽取日K数据，优先使用未训练过的股票",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "展示${settings.displayCount}根 · 训练${settings.trainCount}根 · 屏幕${settings.visibleCount}根",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onStart, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (isLoading) "加载中..." else "开始训练")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSettings) {
                Text("设置")
            }
            Spacer(modifier = Modifier.height(12.dp))
            VipStatusBar(vipManager)
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = LocalMarketColors.current.warning)
            }
        }
    }
}

/**
 * 行情头部（对齐同花顺布局：名称行 + 大号价格 + 右侧四格高低/开/量）。
 * 布局依据：主信息（价格）独占一行、字号最大；副信息（高低开量）两列网格、字号最小。
 * 层级靠字重与字号，不靠透明度（H4 / T3）。
 */
@Composable
private fun StatusBar(
    symbol: String,
    isLoading: Boolean,
    message: String,
    engineState: TrainingUiState,
    hideTime: Boolean,
    onNextRound: () -> Unit,
) {
    val markets = LocalMarketColors.current
    val lastCandle = engineState.allKLineData.getOrNull(engineState.currentIndex)
    val price = engineState.currentPrice ?: lastCandle?.close
    val open = lastCandle?.open
    val high = lastCandle?.high
    val low = lastCandle?.low
    val changePct = if (price != null && open != null && open != 0.0) (price - open) / open else null
    val isUp = (changePct ?: 0.0) >= 0
    val priceColor = if (isUp) markets.bullish else markets.bearish
    val volume = lastCandle?.volume?.toDouble()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                if (hideTime) "盲测" else "日K",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (isLoading) "加载中" else "下一轮",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(ChipShape)
                    .clickable(enabled = !isLoading, onClick = onNextRound)
                    .padding(horizontal = Spacing.m, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = price?.let { "%.2f".format(it) } ?: "--",
                    style = priceStyle(size = 30, bold = true, color = priceColor),
                )
                Text(
                    text = changePct?.let { signedPercent(it) } ?: "--",
                    style = priceStyle(size = 14, bold = true, color = priceColor),
                )
            }
            if (high != null && low != null && open != null) {
                Column {
                    HlRow("高", "%.2f".format(high), markets.bullish)
                    HlRow("低", "%.2f".format(low), markets.bearish)
                }
                Spacer(modifier = Modifier.width(Spacing.m))
                Column {
                    HlRow("开", "%.2f".format(open), MaterialTheme.colorScheme.onSurface)
                    HlRow(
                        "量",
                        volume?.let { formatVolume(it) } ?: "--",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun HlRow(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(18.dp),
        )
        Text(
            value,
            style = priceStyle(size = 12, bold = false, color = valueColor),
        )
    }
}

private fun formatVolume(volume: Double): String = when {
    volume >= 1_0000_0000 -> "%.2f亿".format(volume / 1_0000_0000)
    volume >= 1_0000 -> "%.2f万".format(volume / 1_0000)
    else -> "%.0f".format(volume)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingSettingsDialog(
    settings: TrainingSettings,
    selectedThemeStyle: AppThemeStyle,
    onSave: (TrainingSettingsResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayCountText by remember(settings) { mutableStateOf(settings.displayCount.toString()) }
    var trainCountText by remember(settings) { mutableStateOf(settings.trainCount.toString()) }
    var visibleCountText by remember(settings) { mutableStateOf(settings.visibleCount.toString()) }
    var selectedStyle by remember(selectedThemeStyle) { mutableStateOf(selectedThemeStyle) }
    var hideSymbol by remember(settings) { mutableStateOf(settings.hideSymbol) }
    var hideTime by remember(settings) { mutableStateOf(settings.hideTime) }
    val displayCount = displayCountText.toIntOrNull()
    val trainCount = trainCountText.toIntOrNull()
    val visibleCount = visibleCountText.toIntOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 620.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("训练设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = displayCountText,
                        onValueChange = { displayCountText = it.filter(Char::isDigit).take(4) },
                        label = { Text("展示根数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = trainCountText,
                        onValueChange = { trainCountText = it.filter(Char::isDigit).take(4) },
                        label = { Text("训练根数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = visibleCountText,
                        onValueChange = { visibleCountText = it.filter(Char::isDigit).take(3) },
                        label = { Text("屏幕显示根数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "总根数按展示 + 训练自动计算；屏幕显示根数只控制一屏可见数量，多出的K线可左右滑动查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("双盲设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SettingsSwitchRow(
                        title = "屏蔽股票代码",
                        checked = hideSymbol,
                        onCheckedChange = { hideSymbol = it },
                    )
                    SettingsSwitchRow(
                        title = "屏蔽K线时间",
                        checked = hideTime,
                        onCheckedChange = { hideTime = it },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("界面风格", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppThemeStyles.forEach { style ->
                            ThemeStyleChip(
                                style = style,
                                selected = selectedStyle == style,
                                onClick = { selectedStyle = style },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                TrainingSettingsResult(
                                    settings = TrainingSettings(
                                        displayCount = displayCount ?: settings.displayCount,
                                        trainCount = trainCount ?: settings.trainCount,
                                        visibleCount = visibleCount ?: settings.visibleCount,
                                        hideSymbol = hideSymbol,
                                        hideTime = hideTime,
                                    ),
                                    themeStyle = selectedStyle,
                                ),
                            )
                        },
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ThemeStyleChip(
    style: AppThemeStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val markets = LocalMarketColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ChipShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(1.5.dp, markets.bullishFill) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(0.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        style.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) markets.bullish else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s))
                    ThemeSwatchDots(style)
                }
                Text(
                    style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private data class TrainingSettingsResult(
    val settings: TrainingSettings,
    val themeStyle: AppThemeStyle,
)

@Composable
private fun IndicatorDialog(indicators: IndicatorDisplay, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("技术指标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                IndicatorSwitch("MA5", indicators.showMa5, { indicators.showMa5 = it })
                if (indicators.showMa5) {
                    ParamSlider("周期", indicators.ma5Period, 2..60) { indicators.ma5Period = it }
                }

                IndicatorSwitch("MA10", indicators.showMa10, { indicators.showMa10 = it })
                if (indicators.showMa10) {
                    ParamSlider("周期", indicators.ma10Period, 2..60) { indicators.ma10Period = it }
                }

                IndicatorSwitch("MA20", indicators.showMa20, { indicators.showMa20 = it })
                if (indicators.showMa20) {
                    ParamSlider("周期", indicators.ma20Period, 2..60) { indicators.ma20Period = it }
                }

                IndicatorSwitch("BOLL", indicators.showBoll, { indicators.showBoll = it })
                if (indicators.showBoll) {
                    ParamSlider("周期", indicators.bollPeriod, 5..60) { indicators.bollPeriod = it }
                    FloatSlider("倍数", indicators.bollMultiplier, 1f..4f) { indicators.bollMultiplier = it }
                }

                IndicatorSwitch("MACD", indicators.showMacd, { indicators.showMacd = it })
                if (indicators.showMacd) {
                    ParamSlider("快线", indicators.macdFast, 5..30) { indicators.macdFast = it }
                    ParamSlider("慢线", indicators.macdSlow, 10..60) { indicators.macdSlow = it }
                    ParamSlider("信号", indicators.macdSignal, 2..20) { indicators.macdSignal = it }
                }

                IndicatorSwitch("RSI", indicators.showRsi, { indicators.showRsi = it })
                if (indicators.showRsi) {
                    ParamSlider("周期", indicators.rsiPeriod, 5..30) { indicators.rsiPeriod = it }
                }

                IndicatorSwitch("KDJ", indicators.showKdj, { indicators.showKdj = it })
                if (indicators.showKdj) {
                    ParamSlider("N", indicators.kdjN, 3..30) { indicators.kdjN = it }
                    ParamSlider("K", indicators.kdjK, 2..10) { indicators.kdjK = it }
                    ParamSlider("D", indicators.kdjD, 2..10) { indicators.kdjD = it }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun IndicatorSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ParamSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: $value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FloatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: ${"%.1f".format(value)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

private val DRAWING_COLORS = listOf(
    0xFF42A5F5.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFE040FB.toInt(),
)

@Composable
private fun drawingColors(): List<Int> {
    val colors = LocalMarketColors.current
    return listOf(
        colors.bullish,
        colors.bearish,
        colors.warning,
        colors.line4,
        colors.wick,
        colors.line3,
    ).map { it.toArgb() }
}

private val DRAWING_TOOLS = listOf(
    DrawingTool.HORIZONTAL,
    DrawingTool.TREND_LINE,
)

@Composable
private fun ChartPeriodTabs(
    selected: TrainingChartPeriod,
    onSelected: (TrainingChartPeriod) -> Unit,
) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TrainingChartPeriod.entries.forEach { period ->
            Tab(
                selected = selected == period,
                onClick = { onSelected(period) },
                text = { Text(period.label, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun DrawingToolbar(state: DrawingState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DRAWING_TOOLS.forEach { tool ->
            TextButton(
                onClick = { state.selectTool(tool) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (state.currentTool == tool) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.height(32.dp),
            ) {
                Text(tool.label, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.isDrawing) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { state.cancelDrawing() }, modifier = Modifier.height(32.dp)) {
                Text("退出", style = MaterialTheme.typography.bodySmall)
            }

            drawingColors().forEach { colorLong ->
                val c = Color(colorLong)
                val isSelected = state.currentColor == colorLong
                Canvas(
                    modifier = Modifier
                        .size(if (isSelected) 20.dp else 16.dp)
                        .then(if (isSelected) Modifier.padding(0.dp) else Modifier.padding(2.dp))
                        .clickable { state.setColor(c) },
                ) {
                    drawCircle(color = c)
                    if (isSelected) {
                        drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(1.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { state.undoLast() }, modifier = Modifier.height(32.dp)) {
                Text("撤销", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { state.clearAll() }, modifier = Modifier.height(32.dp)) {
                Text("清除", style = MaterialTheme.typography.bodySmall)
            }
            state.getPendingLabel()?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMarketColors.current.warning,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VipStatusBar(vipManager: com.stock.trainer.data.local.VipManager) {
    val isVip by vipManager.isVipFlow.collectAsState()
    val trainingCount by vipManager.trainingCountFlow.collectAsState()
    val marketColors = LocalMarketColors.current
    if (isVip) {
        Text(
            "支持者模式已开启",
            style = MaterialTheme.typography.bodySmall,
            color = marketColors.warning,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    } else {
        val remaining = (MAX - trainingCount).coerceAtLeast(0)
        val color = when {
            remaining <= 5 -> marketColors.bullish
            remaining <= 15 -> marketColors.warning
            else -> MaterialTheme.colorScheme.outline
        }
        Text(
            "剩余免费训练: $remaining/$MAX",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun VipDialog(
    vipManager: com.stock.trainer.data.local.VipManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isVip by vipManager.isVipFlow.collectAsState()
    val trainingCount by vipManager.trainingCountFlow.collectAsState()
    val marketColors = LocalMarketColors.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("支持作者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "免费可训练 66 次。你可以自行开启支持者模式，解除本设备训练次数限制。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "如果这个工具对你有帮助，欢迎到 TapTap 看看作者的其他作品。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "本应用仅用于历史行情回放与复盘练习，不提供荐股、买卖建议、收益预测或证券投资咨询服务。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { openTapTapAuthorPage(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1677FF)),
                ) {
                    Text("去TapTap看作者其他作品")
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            if (isVip) "支持者模式已开启" else "开启支持者模式",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isVip) marketColors.warning
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (isVip) {
                                "已解除本设备训练次数限制"
                            } else {
                                "已训练${trainingCount}次。此开关不校验付款，请按自己的意愿使用。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = isVip,
                        onCheckedChange = { vipManager.isVip = it },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun openTapTapAuthorPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TAPTAP_AUTHOR_URL))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(TAPTAP_AUTHOR_FALLBACK_URL))
        try {
            context.startActivity(browserIntent)
        } catch (_: ActivityNotFoundException) {
        }
    }
}

private const val MAX = 66
private const val TAPTAP_AUTHOR_URL = "https://www.taptap.cn/user/427517270"
private const val TAPTAP_AUTHOR_FALLBACK_URL = "https://www.taptap.cn/"

@Composable
private fun PositionInfoBar(
    position: com.stock.trainer.domain.engine.Position,
    currentPrice: Double?,
    unrealizedPnl: Double,
) {
    val marketColors = LocalMarketColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "持仓",
                    color = marketColors.bullish,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text("入场 ${"%.2f".format(position.entryPrice)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPnlPercent(unrealizedPnl),
                    color = if (unrealizedPnl >= 0) marketColors.bullish else marketColors.bearish,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "当前 ${currentPrice?.let { "%.2f".format(it) } ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TradeSummaryBar(trades: List<TradeRecord>) {
    val totalPnl = trades.sumOf { it.pnl }
    val wins = trades.count { it.pnl > 0 }
    val marketColors = LocalMarketColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text("${trades.size}笔", style = MaterialTheme.typography.bodySmall)
        Text("胜 $wins/${trades.size}", style = MaterialTheme.typography.bodySmall)
        Text(
            text = formatPnlPercent(totalPnl),
            color = if (totalPnl >= 0) marketColors.bullish else marketColors.bearish,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ModeSelector(
    playMode: PlayMode,
    advanceMode: AdvanceMode,
    onPlayModeChange: (PlayMode) -> Unit,
    onAdvanceModeChange: (AdvanceMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayMode.entries.forEach { mode ->
                ModeChip(
                    label = mode.label,
                    selected = playMode == mode,
                    onClick = { onPlayModeChange(mode) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(AdvanceMode.SINGLE_STEP, AdvanceMode.TWO_STEP).forEach { mode ->
                ModeChip(
                    label = mode.label,
                    selected = advanceMode == mode,
                    onClick = { onAdvanceModeChange(mode) },
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SegmentedChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun ModeActionBar(
    playMode: PlayMode,
    advanceMode: AdvanceMode,
    isStepOne: Boolean,
    isAutoPlaying: Boolean,
    autoIntervalMs: Long,
    isFinished: Boolean,
    canBuy: Boolean,
    canSell: Boolean,
    hasNextCandle: Boolean,
    totalPnl: Double,
    onWatch: () -> Unit,
    onTwoStepBuy: () -> Unit,
    onTwoStepSell: () -> Unit,
    onBidBuy: () -> Unit,
    onBidSell: () -> Unit,
    onCloseBuy: () -> Unit,
    onCloseSell: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onSetAutoInterval: (Long) -> Unit,
    onFinish: () -> Unit,
) {
    val marketColors = LocalMarketColors.current
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text("累计盈亏: ${formatPnlPercent(totalPnl)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (isFinished) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = marketColors.bullishFill),
            ) {
                Text("结束训练，查看结果")
            }
        } else {
            if (playMode == PlayMode.AUTO) {
                AutoPlayBar(isAutoPlaying, autoIntervalMs, onToggleAutoPlay, onSetAutoInterval, onFinish)
                Spacer(modifier = Modifier.height(4.dp))
            }
            when (advanceMode) {
                AdvanceMode.TWO_STEP -> TwoStepActionBar(
                    isStepOne = isStepOne,
                    canBuy = canBuy,
                    canSell = canSell,
                    onWatch = onWatch,
                    onBuy = onTwoStepBuy,
                    onSell = onTwoStepSell,
                    onFinish = onFinish,
                )

                AdvanceMode.SINGLE_STEP -> SingleStepActionBar(
                    canBuy = canBuy,
                    canSell = canSell,
                    hasNextCandle = hasNextCandle,
                    onWatch = onWatch,
                    onBidBuy = onBidBuy,
                    onBidSell = onBidSell,
                    onCloseBuy = onCloseBuy,
                    onCloseSell = onCloseSell,
                    onFinish = onFinish,
                )
            }
        }
    }
}

@Composable
private fun AutoPlayBar(
    isAutoPlaying: Boolean,
    autoIntervalMs: Long,
    onToggle: () -> Unit,
    onSetInterval: (Long) -> Unit,
    onFinish: () -> Unit,
) {
    val marketColors = LocalMarketColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAutoPlaying) marketColors.bearish else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isAutoPlaying) "暂停" else "播放")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("间隔 ${autoIntervalMs / 1000}s", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = autoIntervalMs.toFloat(),
                onValueChange = { onSetInterval(it.toLong()) },
                valueRange = 500f..10000f,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onFinish) {
            Icon(Icons.Default.FastForward, contentDescription = "结束")
        }
    }
}

@Composable
private fun TwoStepActionBar(
    isStepOne: Boolean,
    canBuy: Boolean,
    canSell: Boolean,
    onWatch: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onFinish: () -> Unit,
) {
    val marketColors = LocalMarketColors.current
    Column {
        Text(
            if (isStepOne) "第一步：仅开盘价可见" else "第二步：完整K线已揭晓",
            style = MaterialTheme.typography.bodySmall,
                    color = if (isStepOne) LocalMarketColors.current.warning else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onWatch, modifier = Modifier.weight(1f)) {
                Text(if (isStepOne) "观望" else "下一根")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onBuy,
                enabled = canBuy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = marketColors.bullishFill),
            ) {
                Text("买入")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = onSell,
                enabled = canSell,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = marketColors.bearishFill),
            ) {
                Text("卖出")
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onFinish) {
                Icon(Icons.Default.FastForward, contentDescription = "结束")
            }
        }
    }
}

@Composable
private fun SingleStepActionBar(
    canBuy: Boolean,
    canSell: Boolean,
    hasNextCandle: Boolean,
    onWatch: () -> Unit,
    onBidBuy: () -> Unit,
    onBidSell: () -> Unit,
    onCloseBuy: () -> Unit,
    onCloseSell: () -> Unit,
    onFinish: () -> Unit,
) {
    val marketColors = LocalMarketColors.current
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onWatch, modifier = Modifier.weight(1f)) {
                Text("观望", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = onBidBuy,
                enabled = canBuy && hasNextCandle,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = marketColors.bullishFill),
            ) {
                Text("竞价买", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = onBidSell,
                enabled = canSell && hasNextCandle,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = marketColors.bearishFill),
            ) {
                Text("竞价卖", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // 次要动作：描边而非低透明度填充（C12：禁用/次要用降饱和，不用叠透明）
            OutlinedButton(
                onClick = onCloseBuy,
                enabled = canBuy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = marketColors.bullish),
                border = BorderStroke(1.dp, marketColors.bullishSoft),
            ) {
                Text("收盘买", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedButton(
                onClick = onCloseSell,
                enabled = canSell,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = marketColors.bearish),
                border = BorderStroke(1.dp, marketColors.bearishSoft),
            ) {
                Text("收盘卖", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onFinish) {
                Icon(Icons.Default.FastForward, contentDescription = "结束")
            }
        }
    }
}

@Composable
private fun TrainingResultView(
    result: TrainingResult,
    onNewTraining: () -> Unit,
) {
    val marketColors = LocalMarketColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("训练结束", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ResultRow(
                    "总盈亏",
                    formatPnlPercent(result.totalPnl),
                    if (result.totalPnl >= 0) marketColors.bullish else marketColors.bearish,
                )
                ResultRow("交易次数", "${result.totalTrades}")
                ResultRow("胜率", "%.1f%%".format(result.winRate * 100))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNewTraining,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("下一轮")
        }
    }
}

private fun formatPnlPercent(value: Double): String {
    val percent = value * 100
    return if (percent >= 0) "+${"%.2f".format(percent)}%" else "%.2f%%".format(percent)
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
