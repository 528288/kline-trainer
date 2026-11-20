package com.stock.trainer.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stock.trainer.domain.model.TradeRecord
import com.stock.trainer.domain.model.TrainingSession
import com.stock.trainer.ui.chart.CandlestickChart
import com.stock.trainer.ui.chart.DrawingState
import com.stock.trainer.ui.chart.IndicatorDisplay
import com.stock.trainer.ui.chart.ViewportState
import com.stock.trainer.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史对局") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    Text("加载中", modifier = Modifier.padding(16.dp))
                }

                state.error.isNotEmpty() -> {
                    Text(state.error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }

                state.session != null -> {
                    val session = state.session!!
                    SessionHeader(session, state.trades.size)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        val viewportState = remember { ViewportState() }
                        val indicators = remember { IndicatorDisplay() }
                        val drawingState = remember { DrawingState() }
                        val density = LocalDensity.current
                        val chartWidthPx = with(density) { maxWidth.toPx() }
                        LaunchedEffect(state.chartData.size, chartWidthPx) {
                            val visibleCount = minOf(80, state.chartData.size.coerceAtLeast(1))
                            val drawableWidth = (chartWidthPx - 72f).coerceAtLeast(visibleCount.toFloat())
                            val itemWidth = drawableWidth / visibleCount
                            viewportState.gapWidth = (itemWidth * 0.22f).coerceIn(1f, 4f)
                            viewportState.candleWidth = (itemWidth - viewportState.gapWidth).coerceAtLeast(2f)
                            viewportState.setWindow(
                                size = state.chartData.size,
                                count = visibleCount,
                                anchorIndex = state.chartData.lastIndex.coerceAtLeast(0),
                            )
                        }
                        CandlestickChart(
                            data = state.chartData,
                            viewportState = viewportState,
                            indicators = indicators,
                            drawingState = drawingState,
                            tradeMarkers = state.markers,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(state.trades) { index, trade ->
                            HistoryTradeRow(index + 1, trade)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(session: TrainingSession, tradeCount: Int) {
    val marketColors = LocalMarketColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${session.symbol} · ${session.period}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    formatPnlPercent(session.totalPnl),
                    color = if (session.totalPnl >= 0) marketColors.bullish else marketColors.bearish,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "训练时间 ${formatTime(session.startedAt)}${session.finishedAt?.let { " - ${formatTime(it)}" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                "交易 $tradeCount 笔 · 胜率 ${"%.1f".format((session.winRate ?: 0.0) * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun HistoryTradeRow(index: Int, trade: TradeRecord) {
    val marketColors = LocalMarketColors.current
    val color = if (trade.pnl >= 0) marketColors.bullish else marketColors.bearish
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("第${index}笔", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text(formatPnlPercent(trade.pnl), style = MaterialTheme.typography.bodySmall, color = color)
        }
        Text(
            "${formatTime(trade.entryTime)} 买 @ ${"%.2f".format(trade.entryPrice)}  ->  ${formatTime(trade.exitTime)} 卖 @ ${"%.2f".format(trade.exitPrice)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
