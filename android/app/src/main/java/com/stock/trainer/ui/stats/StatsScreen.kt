package com.stock.trainer.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stock.trainer.data.repository.TrainingStats
import com.stock.trainer.domain.model.TrainingSession
import com.stock.trainer.domain.model.TradeRecord
import com.stock.trainer.ui.theme.LocalMarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onSessionClick: (String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val expandedSessionIds by viewModel.expandedSessionIds.collectAsState()
    val privacySettings by viewModel.privacySettings.collectAsState()
    var pendingDelete by remember { mutableStateOf<SessionWithTrades?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("训练统计") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item { StatsSummaryCard(stats) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { Text("训练记录", style = MaterialTheme.typography.titleSmall) }
            items(sessions) { item ->
                SessionCard(
                    item = item,
                    privacySettings = privacySettings,
                    expanded = item.session.id in expandedSessionIds,
                    onToggle = { viewModel.toggleSession(item.session.id) },
                    onOpen = { onSessionClick(item.session.id) },
                    onDelete = { pendingDelete = item },
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除历史对局") },
            text = {
                Text(
                    "确定删除 ${item.session.symbol} 的这次训练记录吗？相关买卖记录也会一并删除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(item.session.id)
                        pendingDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun StatsSummaryCard(stats: TrainingStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("总体统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem("总交易", "${stats.totalTrades}")
                StatItem("胜率", "%.1f%%".format(stats.winRate * 100))
                StatItem(
                    "总盈亏",
                    formatPnlPercent(stats.totalPnl),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(label: String, value: String) {
    val marketColors = LocalMarketColors.current
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (value.startsWith("+")) marketColors.bullish else if (value.startsWith("-")) marketColors.bearish else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SessionCard(
    item: SessionWithTrades,
    privacySettings: StatsPrivacySettings,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val session = item.session
    val marketColors = LocalMarketColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (privacySettings.hideSymbol) "盲测标的 · ${session.period}" else "${session.symbol} · ${session.period}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatPnlPercent(session.totalPnl),
                    color = if (session.totalPnl >= 0) marketColors.bullish else marketColors.bearish,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.78f),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${session.totalTrades}笔 · 胜率${"%.1f".format((session.winRate ?: 0.0) * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Text(
                "训练时间 ${formatTime(session.startedAt)}${session.finishedAt?.let { " - ${formatTime(it)}" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "点击查看历史K线与BS点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "收起明细" else "展开明细",
                    modifier = Modifier.clickable(onClick = onToggle).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (item.trades.isEmpty()) {
                    Text(
                        "暂无买卖记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    item.trades.forEachIndexed { index, trade ->
                        TradeRow(
                            index = index + 1,
                            session = session,
                            trade = trade,
                            hideTime = privacySettings.hideTime,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeRow(
    index: Int,
    session: TrainingSession,
    trade: TradeRecord,
    hideTime: Boolean,
) {
    val marketColors = LocalMarketColors.current
    val color = if (trade.pnl >= 0) marketColors.bullish else marketColors.bearish
    val entryPoint = session.pointLabel(trade.indexFromNote("entryIndex"), trade.entryTime, hideTime)
    val exitPoint = session.pointLabel(trade.indexFromNote("exitIndex"), trade.exitTime, hideTime)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("第${index}笔", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text(formatPnlPercent(trade.pnl), style = MaterialTheme.typography.bodySmall, color = color)
        }
        Text(
            "买点 $entryPoint @ ${"%.2f".format(trade.entryPrice)} · 卖点 $exitPoint @ ${"%.2f".format(trade.exitPrice)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (!hideTime) {
            Text(
                "${formatTime(trade.entryTime)} → ${formatTime(trade.exitTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun TrainingSession.pointLabel(index: Int?, fallbackTime: Long, hideTime: Boolean): String {
    if (index == null) return if (hideTime) "-" else formatTime(fallbackTime)
    return "K${index - startIndex + 1}"
}
