package com.stock.trainer.ui.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class IndicatorDisplay {
    // MA
    var showMa5 by mutableStateOf(true)
    var ma5Period by mutableIntStateOf(5)
    var showMa10 by mutableStateOf(false)
    var ma10Period by mutableIntStateOf(10)
    var showMa20 by mutableStateOf(false)
    var ma20Period by mutableIntStateOf(20)

    // BOLL
    var showBoll by mutableStateOf(false)
    var bollPeriod by mutableIntStateOf(20)
    var bollMultiplier by mutableFloatStateOf(2f)

    // MACD
    var showMacd by mutableStateOf(false)
    var macdFast by mutableIntStateOf(12)
    var macdSlow by mutableIntStateOf(26)
    var macdSignal by mutableIntStateOf(9)

    // RSI
    var showRsi by mutableStateOf(false)
    var rsiPeriod by mutableIntStateOf(14)

    // KDJ
    var showKdj by mutableStateOf(false)
    var kdjN by mutableIntStateOf(9)
    var kdjK by mutableIntStateOf(3)
    var kdjD by mutableIntStateOf(3)
}
