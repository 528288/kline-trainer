<template>
  <div class="trainer-page">
    <!-- 控制面板 -->
    <div class="control-panel card">
      <div class="control-row">
        <div class="info-group">
          <span class="info-label">股票:</span>
          <span class="info-value">{{ config.blindCode ? '******' : stockCode }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">名称:</span>
          <span class="info-value">{{ config.blindName ? '****' : stockName }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">模式:</span>
          <span class="info-value">{{ config.advanceMode === 'two_step' ? '两步' : '一步' }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">进度:</span>
          <span class="info-value">{{ trainCursor + 1 }}/{{ trainCount }}
            <span v-if="isTwoStep" class="phase-tag" :class="phase">({{ phase === 'open' ? '开盘' : '收盘' }})</span>
          </span>
        </div>
      </div>

      <!-- K线数据统计 -->
      <div class="control-row bar-stats" v-if="currentBar">
        <div class="info-group">
          <span class="info-label">开盘</span>
          <span class="info-value">{{ currentBar.open.toFixed(2) }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">最高</span>
          <span class="info-value up">{{ currentBar.high.toFixed(2) }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">最低</span>
          <span class="info-value down">{{ currentBar.low.toFixed(2) }}</span>
        </div>
        <div class="info-group">
          <span class="info-label">收盘</span>
          <span class="info-value" :class="barChange !== null ? (barChange >= 0 ? 'up' : 'down') : ''">{{ currentBar.close.toFixed(2) }}</span>
        </div>
        <div class="info-group" v-if="barChange !== null">
          <span class="info-label">涨跌</span>
          <span class="info-value" :class="barChange >= 0 ? 'up' : 'down'">{{ barChange >= 0 ? '+' : '' }}{{ barChange.toFixed(2) }}</span>
        </div>
        <div class="info-group" v-if="barChangePct !== null">
          <span class="info-label">涨跌幅</span>
          <span class="info-value" :class="barChangePct >= 0 ? 'up' : 'down'">{{ barChangePct >= 0 ? '+' : '' }}{{ barChangePct.toFixed(2) }}%</span>
        </div>
        <div class="info-group" v-if="barAmplitude !== null">
          <span class="info-label">振幅</span>
          <span class="info-value">{{ barAmplitude.toFixed(2) }}%</span>
        </div>
      </div>

      <!-- 全局控制按钮行 -->
      <div class="control-row global-actions">
        <button class="btn btn-primary" @click="advance" :disabled="isDone || !sessionData">
          <i class="fas fa-step-forward"></i>
          <span>{{ advanceLabel }}</span>
        </button>
        <button :class="['btn', autoMode ? 'btn-warning' : 'btn-secondary']" @click="toggleAuto" :disabled="isDone || !sessionData">
          <i :class="autoMode ? 'fas fa-pause' : 'fas fa-play'"></i>
          <span>{{ autoMode ? '停止自动' : '自动' }}</span>
        </button>
        <button class="btn btn-success" @click="nextRound">
          <i class="fas fa-random"></i>
          <span>下一轮</span>
        </button>
        <button class="btn btn-secondary" @click="openSaveDialog" :disabled="!sessionData || savingRun">
          <i class="fas fa-save"></i>
          <span>保存</span>
        </button>
        <button class="btn btn-secondary" @click="openImportDialog" :disabled="importLoading">
          <i class="fas fa-folder-open"></i>
          <span>导入</span>
        </button>
        <button :class="vipButtonClass" @click="openVipDialog">
          <i :class="isVip ? 'fas fa-crown' : 'fas fa-heart'"></i>
          <span>{{ vipButtonText }}</span>
        </button>
        <button class="btn btn-secondary" @click="showSettings = true">
          <i class="fas fa-cog"></i>
          <span>设置</span>
        </button>
      </div>
    </div>

    <!-- 周期标签页 -->
    <div class="period-tabs card">
      <button v-for="p in visibleTabPeriods" :key="p"
        :class="['period-tab', { active: currentPeriod === p }]"
        @click="switchPeriod(p)">
        {{ periodLabel(p) }}
      </button>
      <div class="period-more" v-if="visibleExtraPeriods.length">
        <button class="period-tab more" @click="showMorePeriods = !showMorePeriods">
          更多 <i :class="showMorePeriods ? 'fas fa-caret-up' : 'fas fa-caret-down'"></i>
        </button>
        <div v-if="showMorePeriods" class="period-dropdown">
          <button v-for="p in visibleExtraPeriods" :key="p"
            :class="['period-tab', { active: currentPeriod === p }]"
            @click="switchPeriod(p); showMorePeriods = false">
            {{ periodLabel(p) }}
          </button>
        </div>
      </div>
    </div>

    <!-- 消息提示 -->
    <div v-if="message" :class="['toast-msg', `alert-${messageType}`]">
      <i :class="['fas', messageType === 'success' ? 'fa-check-circle' : messageType === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle']"></i>
      <span>{{ message }}</span>
    </div>

    <!-- 指标和画线工具栏 -->
    <div class="toolbar card">
      <div class="toolbar-section">
        <span class="toolbar-label">指标:</span>
        <button v-for="ind in subIndicators" :key="ind.name"
          :class="['toolbar-btn', { active: ind.active }]"
          @click="toggleIndicator(ind)">
          {{ ind.label }}
        </button>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-section">
        <span class="toolbar-label">画线:</span>
        <button v-for="tool in drawingTools" :key="tool.name"
          :class="['toolbar-btn', { active: activeDrawingTool === tool.name }]"
          @click="selectDrawingTool(tool.name)">
          <i :class="tool.icon"></i>
          {{ tool.label }}
        </button>
        <button class="toolbar-btn danger" @click="clearOverlays" title="清除所有画线">
          <i class="fas fa-trash"></i> 清除
        </button>
      </div>
    </div>

    <!-- 主区域：K线图 + 右侧交易面板 -->
    <div class="main-area">
      <div class="chart-container card">
        <div id="trainer-chart" ref="chartRef"></div>
      </div>

      <div class="right-panel">
        <!-- 持仓信息 -->
        <div class="panel-card card">
          <div class="position-info-inline">
            <span v-if="position" class="position-badge long">
              持仓 多 @ {{ position.entryPrice.toFixed(2) }}
              <span :class="currentPnl >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ currentPnl >= 0 ? '+' : '' }}{{ currentPnl.toFixed(2) }}%</span>
            </span>
            <span v-else class="position-badge empty">空仓</span>
            <span v-if="pendingOrder" class="pending-badge">
              挂单: {{ pendingOrder.side === 'buy' ? '竞买' : '竞卖' }}
            </span>
          </div>
        </div>

        <!-- 交易操作按钮 -->
        <div class="panel-card card trade-actions-card">
          <template v-if="config.advanceMode === 'two_step'">
            <button class="trade-btn buy" @click="twoStepBuy" :disabled="!canAct" :title="'买入 [' + s('two_buy') + ']'">
              <span class="trade-btn-text">买入</span><span class="key-hint">{{ s('two_buy') }}</span>
            </button>
            <button class="trade-btn sell" @click="twoStepSell" :disabled="!canAct" :title="'卖出 [' + s('two_sell') + ']'">
              <span class="trade-btn-text">卖出</span><span class="key-hint">{{ s('two_sell') }}</span>
            </button>
            <button class="trade-btn wait" @click="twoStepWait" :disabled="!canAct" :title="'观望 [' + s('two_wait') + ']'">
              <span class="trade-btn-text">观望</span><span class="key-hint">{{ s('two_wait') }}</span>
            </button>
          </template>
          <template v-else>
            <button class="trade-btn buy" @click="auctionBuy" :disabled="!canAct" :title="'竞价买入 [' + s('one_auctionBuy') + ']'">
              <span class="trade-btn-text">竞价买入</span><span class="key-hint">{{ s('one_auctionBuy') }}</span>
            </button>
            <button class="trade-btn sell" @click="auctionSell" :disabled="!canAct" :title="'竞价卖出 [' + s('one_auctionSell') + ']'">
              <span class="trade-btn-text">竞价卖出</span><span class="key-hint">{{ s('one_auctionSell') }}</span>
            </button>
            <button class="trade-btn buy" @click="closeBuy" :disabled="!canAct" :title="'收盘买入 [' + s('one_closeBuy') + ']'">
              <span class="trade-btn-text">收盘买入</span><span class="key-hint">{{ s('one_closeBuy') }}</span>
            </button>
            <button class="trade-btn sell" @click="closeSell" :disabled="!canAct" :title="'收盘卖出 [' + s('one_closeSell') + ']'">
              <span class="trade-btn-text">收盘卖出</span><span class="key-hint">{{ s('one_closeSell') }}</span>
            </button>
            <button class="trade-btn wait" @click="oneStepWait" :disabled="!canAct" :title="'观望 [' + s('one_wait') + ']'">
              观望<span class="key-hint">{{ s('one_wait') }}</span>
            </button>
          </template>
        </div>

        <!-- 交易记录 -->
        <div ref="tradeLogRef" class="trade-log card" v-if="trades.length > 0 || isDone">
          <h4 class="log-title">交易记录</h4>
          <div class="log-stats-list">
            <div class="stat-row main">
              <span class="stat-label">累计盈亏</span>
              <span class="stat-val" :class="cumulativePnl >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ cumulativePnl >= 0 ? '+' : '' }}{{ cumulativePnl.toFixed(2) }}%</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">总盈亏</span>
              <span class="stat-val" :class="totalPnl >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ totalPnl >= 0 ? '+' : '' }}{{ totalPnl.toFixed(2) }}%</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">胜率</span>
              <span class="stat-val">{{ winRate }}%</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">交易</span>
              <span class="stat-val">{{ tradeCount }} 笔</span>
            </div>
          </div>
          <div class="log-list">
            <template v-for="(t, i) in trades" :key="i">
              <div class="log-item">
                <span class="log-index">#{{ i + 1 }}</span>
                <span :class="t.side === 'buy' ? 'log-buy' : 'log-sell'">{{ tradeTypeLabel(t) }}</span>
                <span class="log-price">{{ t.price.toFixed(2) }}</span>
                <span class="log-time">{{ formatTradeTime(t) }}</span>
              </div>
              <div v-if="t.pnlPercent != null" class="log-pnl-row">
                <span class="log-pnl-label">第{{ closedTradeNumber(i) }}笔收益</span>
                <span :class="['log-pnl-value', t.pnlPercent >= 0 ? 'pnl-positive' : 'pnl-negative']">
                  {{ t.pnlPercent >= 0 ? '+' : '' }}{{ t.pnlPercent.toFixed(2) }}%
                </span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 设置弹窗 -->
    <div class="settings-modal-overlay" v-if="showSettings" @click.self="closeSettings">
      <div class="settings-modal">
        <div class="settings-modal-header">
          <h3>K线训练设置</h3>
          <button class="modal-close" @click="closeSettings">&times;</button>
        </div>
        <div class="settings-modal-body">
          <div class="settings-group">
            <h4>K线数量</h4>
            <div class="form-row">
              <label>总根数: <input type="number" v-model.number="config.totalBars" min="10" max="2000"></label>
              <label>展示根数: <input type="number" v-model.number="config.displayCount" min="1"></label>
              <label>训练根数: <input type="number" v-model.number="config.trainCount" min="1"></label>
            </div>
            <p class="form-hint" v-if="config.displayCount + config.trainCount > config.totalBars">
              ⚠ 展示+训练超过总根数，将自动调整总根数
            </p>
          </div>
          <div class="settings-group">
            <h4>推进模式</h4>
            <label class="radio-label"><input type="radio" v-model="config.advanceMode" value="two_step"> 两步模式（先开盘后收盘）</label>
            <label class="radio-label"><input type="radio" v-model="config.advanceMode" value="one_step"> 一步模式（直接完整K线）</label>
          </div>
          <div class="settings-group">
            <h4>自动推进</h4>
            <label>间隔秒数: <input type="number" v-model.number="config.autoInterval" min="1" max="60" step="0.5"></label>
          </div>
          <div class="settings-group">
            <h4>买卖标记</h4>
            <label class="checkbox-label"><input type="checkbox" v-model="config.showMarkers"> 在K线图上显示买卖标记点</label>
          </div>
          <div class="settings-group">
            <h4>线条颜色</h4>
            <div class="color-grid">
              <label>上涨K线 <input type="color" v-model="config.lineColors.up"></label>
              <label>下跌K线 <input type="color" v-model="config.lineColors.down"></label>
              <label>无涨跌K线 <input type="color" v-model="config.lineColors.noChange"></label>
              <label>买入标记 <input type="color" v-model="config.lineColors.buyMarker"></label>
              <label>卖出标记 <input type="color" v-model="config.lineColors.sellMarker"></label>
              <label v-for="tool in drawingTools" :key="tool.name">{{ tool.label }} <input type="color" v-model="config.drawingColors[tool.name]"></label>
            </div>
          </div>
          <div class="settings-group">
            <h4>快捷键</h4>
            <div class="form-row" v-if="config.advanceMode === 'two_step'">
              <label>买入: <input type="text" v-model="config.shortcuts.two_buy" maxlength="1" style="width:40px;text-align:center"></label>
              <label>卖出: <input type="text" v-model="config.shortcuts.two_sell" maxlength="1" style="width:40px;text-align:center"></label>
              <label>观望: <input type="text" v-model="config.shortcuts.two_wait" maxlength="1" style="width:40px;text-align:center"></label>
            </div>
            <div class="form-row" v-else>
              <label>竞买: <input type="text" v-model="config.shortcuts.one_auctionBuy" maxlength="1" style="width:40px;text-align:center"></label>
              <label>竞卖: <input type="text" v-model="config.shortcuts.one_auctionSell" maxlength="1" style="width:40px;text-align:center"></label>
              <label>收买: <input type="text" v-model="config.shortcuts.one_closeBuy" maxlength="1" style="width:40px;text-align:center"></label>
              <label>收卖: <input type="text" v-model="config.shortcuts.one_closeSell" maxlength="1" style="width:40px;text-align:center"></label>
              <label>观望: <input type="text" v-model="config.shortcuts.one_wait" maxlength="1" style="width:40px;text-align:center"></label>
            </div>
          </div>
          <div class="settings-group">
            <h4>双盲选项</h4>
            <label class="checkbox-label"><input type="checkbox" v-model="config.blindCode"> 隐藏股票代码</label>
            <label class="checkbox-label"><input type="checkbox" v-model="config.blindName"> 隐藏股票名称</label>
            <label class="checkbox-label"><input type="checkbox" v-model="config.blindTime"> 隐藏K线时间</label>
          </div>
        </div>
        <div class="settings-modal-footer">
          <button class="btn btn-secondary" @click="closeSettings">取消</button>
          <button class="btn btn-primary" @click="saveSettings">保存</button>
        </div>
      </div>
    </div>

    <!-- 保存弹窗 -->
    <div class="settings-modal-overlay" v-if="showSaveDialog" @click.self="closeSaveDialog">
      <div class="settings-modal record-modal small">
        <div class="settings-modal-header">
          <h3>保存训练记录</h3>
          <button class="modal-close" @click="closeSaveDialog">&times;</button>
        </div>
        <div class="settings-modal-body">
          <div class="settings-group">
            <h4>保存ID</h4>
            <div class="record-form">
              <label>
                新ID
                <input type="text" v-model.trim="saveGroupInput" placeholder="输入后优先保存到该ID">
              </label>
              <label>
                历史ID
                <select v-model="selectedSaveGroupId">
                  <option value="">请选择历史ID</option>
                  <option v-for="id in saveGroupIds" :key="id" :value="id">{{ id }}</option>
                </select>
              </label>
            </div>
            <p class="form-hint">填写新ID会保存到新ID；不填写时保存到下拉框选中的历史ID。</p>
          </div>
          <div class="save-preview">
            <div><span>股票</span><strong>{{ stockCode }} {{ stockName }}</strong></div>
            <div><span>训练进度</span><strong>{{ Math.min(trainCursor + 1, trainCount) }}/{{ trainCount }}</strong></div>
            <div><span>交易笔数</span><strong>{{ tradeCount }} 笔</strong></div>
            <div><span>累计盈亏</span><strong :class="cumulativePnl >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ formatPercent(cumulativePnl) }}</strong></div>
          </div>
        </div>
        <div class="settings-modal-footer">
          <button class="btn btn-secondary" @click="closeSaveDialog">取消</button>
          <button class="btn btn-primary" @click="saveCurrentRun" :disabled="savingRun || !saveTargetGroupId">
            {{ savingRun ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 导入弹窗 -->
    <div class="settings-modal-overlay" v-if="showImportDialog" @click.self="closeImportDialog">
      <div class="settings-modal record-modal">
        <div class="settings-modal-header">
          <h3>导入训练记录</h3>
          <button class="modal-close" @click="closeImportDialog">&times;</button>
        </div>
        <div class="settings-modal-body">
          <template v-if="!selectedImportedRun">
            <div class="settings-group">
              <h4>选择保存ID</h4>
              <div class="record-form">
                <label>
                  历史ID
                  <select v-model="selectedImportGroupId" @change="loadImportRuns">
                    <option value="">请选择保存ID</option>
                    <option v-for="id in importGroupIds" :key="id" :value="id">{{ id }}</option>
                  </select>
                </label>
              </div>
            </div>
            <div v-if="importLoading" class="record-empty">加载中...</div>
            <div v-else-if="!selectedImportGroupId" class="record-empty">请选择一个保存ID。</div>
            <div v-else-if="!importRuns.length" class="record-empty">该ID下暂无训练记录。</div>
            <div v-else class="record-list">
              <button v-for="run in importRuns" :key="run.id" class="record-row" @click="openImportedRun(run)">
                <span class="record-main">
                  <strong>{{ run.stock_code || '-' }} {{ run.stock_name || '' }}</strong>
                  <small>{{ run.trained_at || '-' }} · {{ advanceModeLabel(run.advance_mode) }}</small>
                </span>
                <span class="record-side">
                  <strong :class="Number(run.cumulative_pnl || 0) >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ formatPercent(run.cumulative_pnl) }}</strong>
                  <small>{{ run.trade_count || 0 }} 笔</small>
                </span>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="record-detail-head">
              <button class="btn btn-secondary" @click="backToImportList">
                <i class="fas fa-arrow-left"></i>
                <span>返回列表</span>
              </button>
              <div>
                <h4>{{ selectedImportedRun.stock_code || '-' }} {{ selectedImportedRun.stock_name || '' }}</h4>
                <p>{{ selectedImportedRun.trained_at || '-' }}</p>
              </div>
            </div>
            <div class="record-detail-stats">
              <div><span>累计盈亏</span><strong :class="Number(selectedImportedRun.cumulative_pnl || 0) >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ formatPercent(selectedImportedRun.cumulative_pnl) }}</strong></div>
              <div><span>总盈亏</span><strong :class="Number(selectedImportedRun.total_pnl || 0) >= 0 ? 'pnl-positive' : 'pnl-negative'">{{ formatPercent(selectedImportedRun.total_pnl) }}</strong></div>
              <div><span>胜率</span><strong>{{ Number(selectedImportedRun.win_rate || 0).toFixed(0) }}%</strong></div>
              <div><span>交易</span><strong>{{ selectedImportedRun.trade_count || 0 }} 笔</strong></div>
            </div>
            <div class="record-trade-list">
              <template v-if="importedRunTrades(selectedImportedRun).length">
                <div v-for="(t, i) in importedRunTrades(selectedImportedRun)" :key="i" class="record-trade">
                  <span class="log-index">#{{ i + 1 }}</span>
                  <span :class="t.side === 'buy' ? 'log-buy' : 'log-sell'">{{ tradeTypeLabel(t) }}</span>
                  <span class="log-price">{{ Number(t.price || 0).toFixed(2) }}</span>
                  <span class="log-time">{{ formatImportedTradeTime(t, selectedImportedRun) }}</span>
                  <span v-if="t.pnlPercent != null" :class="['record-pnl', Number(t.pnlPercent) >= 0 ? 'pnl-positive' : 'pnl-negative']">
                    {{ formatPercent(t.pnlPercent) }}
                  </span>
                </div>
              </template>
              <div v-else class="record-empty">这局没有买卖点记录。</div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- VIP/支持者弹窗 -->
    <div class="settings-modal-overlay" v-if="showVipDialog" @click.self="closeVipDialog">
      <div class="settings-modal vip-modal">
        <div class="settings-modal-header">
          <h3>支持作者</h3>
          <button class="modal-close" @click="closeVipDialog">&times;</button>
        </div>
        <div class="settings-modal-body">
          <div :class="['vip-status-summary', { vip: isVip }]">
            <i :class="isVip ? 'fas fa-crown' : 'fas fa-heart'"></i>
            <div>
              <strong>{{ isVip ? '支持者模式已开启' : `剩余免费训练: ${remainingFree}/${maxFreeTrainings}` }}</strong>
              <span v-if="!isVip">免费次数用完后，可自行开启支持者模式继续训练。</span>
              <span v-else>当前设备训练次数限制已解除。</span>
            </div>
          </div>

          <div class="vip-copy">
            <p>免费可训练 {{ maxFreeTrainings }} 次。你可以自行开启支持者模式，解除本设备训练次数限制。</p>
            <p>如果这个工具对你有帮助，也可以自愿支持作者 9.9 元。付款不是使用前提，不付款也可以继续体验或自行开启。</p>
          </div>

          <div class="vip-pay-box">
            <img src="/qr_code.png" alt="自愿支持二维码" class="vip-qr">
            <button class="btn btn-primary" @click="openPaymentPage">
              <i class="fas fa-external-link-alt"></i>
              <span>自愿支持 9.9 元</span>
            </button>
          </div>

          <div class="vip-switch-row">
            <div>
              <strong>{{ isVip ? '支持者模式已开启' : '开启支持者模式' }}</strong>
              <span v-if="!isVip">已训练{{ trainingCount }}次。此开关不校验付款，请按自己的意愿使用。</span>
              <span v-else>关闭后会继续使用当前已记录的免费训练次数。</span>
            </div>
            <label class="vip-switch">
              <input type="checkbox" :checked="isVip" :disabled="vipSaving" @change="setVipMode($event.target.checked)">
              <span></span>
            </label>
          </div>

          <p class="vip-disclaimer">本应用仅用于历史行情回放与复盘练习，不提供荐股、买卖建议、收益预测或证券投资咨询服务。</p>
        </div>
        <div class="settings-modal-footer">
          <button class="btn btn-secondary" @click="closeVipDialog" :disabled="vipSaving">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { init, dispose, registerOverlay, getSupportedOverlays } from 'klinecharts'
import { trainerApi } from '@/api'

const registerAmplitudeRuler = () => {
  if (getSupportedOverlays().includes('amplitudeRuler')) return
  registerOverlay({
    name: 'amplitudeRuler',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates, overlay }) => {
      if (coordinates.length < 2 || overlay.points.length < 2) return []
      const [start, end] = coordinates
      const startValue = Number(overlay.points[0]?.value)
      const endValue = Number(overlay.points[1]?.value)
      if (!Number.isFinite(startValue) || !Number.isFinite(endValue) || startValue === 0) return []
      const change = endValue - startValue
      const percent = (change / startValue) * 100
      const text = `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%  ${change >= 0 ? '+' : ''}${change.toFixed(2)}`
      const labelX = (start.x + end.x) / 2
      const labelY = (start.y + end.y) / 2
      return [
        { type: 'line', attrs: { coordinates: [start, end] } },
        { type: 'line', attrs: { coordinates: [{ x: start.x, y: start.y }, { x: end.x, y: start.y }] } },
        { type: 'line', attrs: { coordinates: [{ x: end.x, y: start.y }, { x: end.x, y: end.y }] } },
        { type: 'text', attrs: { x: labelX, y: labelY, text, baseline: 'bottom', align: 'center' } }
      ]
    },
    performEventPressedMove: ({ points, performPointIndex, performPoint }) => {
      if (!points[performPointIndex]) return
      points[performPointIndex].timestamp = performPoint.timestamp
      points[performPointIndex].dataIndex = performPoint.dataIndex
      points[performPointIndex].value = performPoint.value
    }
  })
}

registerAmplitudeRuler()

const registerRectangle = () => {
  if (getSupportedOverlays().includes('rectangle')) return
  registerOverlay({
    name: 'rectangle',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates, overlay }) => {
      if (coordinates.length < 2 || overlay.points.length < 2) return []
      const [p1, p2] = coordinates
      const x = Math.min(p1.x, p2.x)
      const y = Math.min(p1.y, p2.y)
      const w = Math.abs(p2.x - p1.x)
      const h = Math.abs(p2.y - p1.y)
      return [{
        type: 'polygon',
        attrs: { coordinates: [{ x, y }, { x: x + w, y }, { x: x + w, y: y + h }, { x, y: y + h }] }
      }]
    },
    performEventPressedMove: ({ points, performPointIndex, performPoint }) => {
      if (!points[performPointIndex]) return
      points[performPointIndex].timestamp = performPoint.timestamp
      points[performPointIndex].dataIndex = performPoint.dataIndex
      points[performPointIndex].value = performPoint.value
    }
  })
}

registerRectangle()

// ============ 图表引用 ============
const chartRef = ref(null)
let chart = null
let currentDrawingOverlayId = null

// ============ 配置 ============
const config = reactive({
  totalBars: 300,
  displayCount: 150,
  trainCount: 150,
  advanceMode: 'two_step',
  autoInterval: 2,
  showMarkers: true,
  blindCode: false,
  blindName: false,
  blindTime: false,
  lineColors: {
    up: '#ef4444',
    down: '#10b981',
    noChange: '#888888',
    buyMarker: '#10b981',
    sellMarker: '#ef4444'
  },
  drawingColors: {
    segment: '#2563eb',
    straightLine: '#2563eb',
    horizontalStraightLine: '#f59e0b',
    rayLine: '#8b5cf6',
    fibonacciLine: '#06b6d4',
    parallelStraightLine: '#64748b',
    amplitudeRuler: '#f97316',
    rectangle: '#22c55e'
  },
  // 快捷键：key 名 → 按键字符
  shortcuts: {
    two_buy: '1', two_sell: '2', two_wait: '3',
    one_auctionBuy: '1', one_auctionSell: '2', one_closeBuy: '3', one_closeSell: '4', one_wait: '5'
  }
})

// 快捷键取值辅助
const s = (key) => config.shortcuts[key] || '?'

// ============ 周期 ============
const currentPeriod = ref('1d')
const tabPeriods = ['1d', 'week', 'month', '15m', '30m', '60m']
const extraPeriods = ['3d', '5d', 'year', '120m']
const showMorePeriods = ref(false)

const periodHasData = (period) => {
  if (!sessionData.value?.bars) return true
  const periodData = sessionData.value.bars[period]
  if (!periodData) return false
  return (periodData.display?.length || 0) + (periodData.train?.length || 0) > 0
}

const visibleTabPeriods = computed(() => tabPeriods.filter(periodHasData))
const visibleExtraPeriods = computed(() => extraPeriods.filter(periodHasData))

const ensureCurrentPeriodVisible = () => {
  if (periodHasData(currentPeriod.value)) return
  currentPeriod.value = visibleTabPeriods.value[0] || visibleExtraPeriods.value[0] || '1d'
  showMorePeriods.value = false
}

// ============ 指标 ============
const subIndicators = reactive([
  { name: 'MA', label: '均线', active: false, paneId: null, stacked: true },
  { name: 'VOL', label: '成交量', active: false, paneId: null },
  { name: 'MACD', label: 'MACD', active: false, paneId: null },
  { name: 'KDJ', label: 'KDJ', active: false, paneId: null },
])

const getDrawingOverlayStyles = (toolName) => {
  const color = config.drawingColors[toolName] || '#2563eb'
  return {
    line: { color },
    text: { color },
    polygon: { borderColor: color }
  }
}

const applyChartColors = () => {
  if (!chart) return
  chart.setStyles({
    candle: {
      bar: {
        upColor: config.lineColors.up,
        downColor: config.lineColors.down,
        noChangeColor: config.lineColors.noChange,
        upBorderColor: config.lineColors.up,
        downBorderColor: config.lineColors.down,
        noChangeBorderColor: config.lineColors.noChange,
        upWickColor: config.lineColors.up,
        downWickColor: config.lineColors.down,
        noChangeWickColor: config.lineColors.noChange
      },
      priceMark: {
        high: { color: config.lineColors.up },
        low: { color: config.lineColors.down }
      }
    },
    indicator: {
      ohlc: {
        upColor: config.lineColors.up,
        downColor: config.lineColors.down,
        noChangeColor: config.lineColors.noChange
      },
      bars: [{
        upColor: config.lineColors.up,
        downColor: config.lineColors.down,
        noChangeColor: config.lineColors.noChange
      }]
    }
  })
}

const ensureIndicators = () => {
  if (!chart) return
  subIndicators.forEach(ind => {
    if (ind.active && !ind.paneId) {
      if (ind.stacked) {
        ind.paneId = chart.createIndicator(ind.name, true, { id: 'candle_pane' })
      } else {
        ind.paneId = chart.createIndicator(ind.name, false, { height: 60 })
      }
    }
  })
}

const toggleIndicator = (ind) => {
  if (!chart) return
  ind.active = !ind.active
  if (ind.active) {
    if (ind.stacked) {
      ind.paneId = chart.createIndicator(ind.name, true, { id: 'candle_pane' })
    } else {
      ind.paneId = chart.createIndicator(ind.name, false, { height: 60 })
    }
  } else if (ind.paneId) {
    chart.removeIndicator(ind.paneId, ind.name)
    ind.paneId = null
  }
}

// ============ 画线持久化 ============
const getCurrentCodeKey = () => (stockCode.value || '').trim().toUpperCase()

const getDrawingScopeKey = (code = getCurrentCodeKey(), drawingPeriod = currentPeriod.value) => `${code}::${drawingPeriod}`

const loadPersistedDrawings = () => {
  try {
    const raw = localStorage.getItem(DRAWING_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch { return {} }
}

const savePersistedDrawings = (registry) => {
  try { localStorage.setItem(DRAWING_STORAGE_KEY, JSON.stringify(registry)) } catch {}
}

const getRequiredDrawingPointCount = (toolName) => ({
  horizontalStraightLine: 1,
  parallelStraightLine: 3
}[toolName] || 2)

const normalizePersistedDrawing = (drawing) => {
  if (!drawing || !drawing.name || !Array.isArray(drawing.points)) return null
  const code = String(drawing.code || '').trim().toUpperCase()
  const drawingPeriod = String(drawing.period || '').trim()
  if (!code || !drawingPeriod) return null
  const points = drawing.points.map(p => ({ timestamp: p.timestamp, value: p.value }))
    .filter(p => typeof p.timestamp === 'number' && typeof p.value === 'number')
  if (points.length < getRequiredDrawingPointCount(drawing.name)) return null
  return {
    name: drawing.name,
    code,
    period: drawingPeriod,
    points,
    styles: drawing.styles || null,
    extendData: drawing.extendData || null
  }
}

const getRelevantDrawings = () => {
  const registry = loadPersistedDrawings()
  const codeKey = getCurrentCodeKey()
  const drawings = []
  Object.values(registry).forEach(value => {
    if (!Array.isArray(value)) return
    value.forEach(item => {
      const drawing = normalizePersistedDrawing(item)
      if (!drawing || drawing.code !== codeKey) return
      if (drawing.period !== currentPeriod.value) return
      drawings.push(drawing)
    })
  })
  return drawings
}

const removeUserDrawingOverlaysFromChart = () => {
  if (!chart) return
  chart.removeOverlay({ groupId: USER_DRAWING_GROUP })
}

const rebuildUserDrawingOverlays = () => {
  if (!chart) return
  removeUserDrawingOverlaysFromChart()
  const drawings = getRelevantDrawings()
  if (!drawings.length) return
  chart.createOverlay(drawings.map(d => ({
    name: d.name,
    groupId: USER_DRAWING_GROUP,
    lock: false,
    visible: true,
    points: d.points,
    styles: getDrawingOverlayStyles(d.name),
    extendData: { ...(d.extendData || {}), isUserDrawing: true, code: d.code, period: d.period }
  })))
}

const resolveOverlayId = (overlayId) => Array.isArray(overlayId) ? overlayId[0] : overlayId

const persistOverlay = (overlay) => {
  if (!overlay || overlay.groupId !== USER_DRAWING_GROUP || !Array.isArray(overlay.points) || !overlay.points.length) return false
  const points = overlay.points
    .filter(Boolean)
    .map(p => ({ timestamp: p.timestamp, value: p.value }))
    .filter(p => typeof p.timestamp === 'number' && typeof p.value === 'number')
  if (points.length < getRequiredDrawingPointCount(overlay.name)) return false
  const extendData = overlay.extendData && typeof overlay.extendData === 'object' ? overlay.extendData : {}
  const code = String(extendData.code || getCurrentCodeKey()).trim().toUpperCase()
  const drawingPeriod = String(extendData.period || currentPeriod.value).trim()
  const scopeKey = getDrawingScopeKey(code, drawingPeriod)
  const registry = loadPersistedDrawings()
  const drawingList = Array.isArray(registry[scopeKey]) ? registry[scopeKey] : []
  drawingList.push({
    name: overlay.name, code, period: drawingPeriod, points,
    styles: overlay.styles || null,
    extendData: { ...extendData, code, period: drawingPeriod, isUserDrawing: true }
  })
  registry[scopeKey] = drawingList
  savePersistedDrawings(registry)
  return true
}

const persistOverlayById = (overlayId) => {
  const resolvedId = resolveOverlayId(overlayId)
  if (!chart || !resolvedId) return false
  return persistOverlay(chart.getOverlayById(resolvedId))
}

const removePendingDrawingOverlay = () => {
  if (!chart || !currentDrawingOverlayId) return
  chart.removeOverlay({ id: currentDrawingOverlayId })
  currentDrawingOverlayId = null
}

const startDrawing = (toolName) => {
  if (!chart) return
  const overlayId = chart.createOverlay({
    name: toolName,
    groupId: USER_DRAWING_GROUP,
    lock: false,
    visible: true,
    styles: getDrawingOverlayStyles(toolName),
    extendData: { isUserDrawing: true, code: getCurrentCodeKey(), period: currentPeriod.value },
    onDrawEnd: event => {
      try {
        persistOverlay(event?.overlay) || persistOverlayById(event?.overlay?.id || overlayId)
      } finally {
        currentDrawingOverlayId = null
        activeDrawingTool.value = null
      }
    },
    onRemoved: event => {
      const removedId = event?.overlay?.id || overlayId
      if (removedId === currentDrawingOverlayId) currentDrawingOverlayId = null
      if (activeDrawingTool.value === toolName) activeDrawingTool.value = null
    }
  })
  currentDrawingOverlayId = resolveOverlayId(overlayId)
}

const selectDrawingTool = (toolName) => {
  if (!chart) return
  if (activeDrawingTool.value === toolName) {
    removePendingDrawingOverlay()
    activeDrawingTool.value = null
    return
  }
  removePendingDrawingOverlay()
  activeDrawingTool.value = toolName
  startDrawing(toolName)
}

const clearOverlays = () => {
  if (!chart) return
  currentDrawingOverlayId = null
  const registry = loadPersistedDrawings()
  const scopeKey = getDrawingScopeKey(getCurrentCodeKey(), currentPeriod.value)
  registry[scopeKey] = []
  savePersistedDrawings(registry)
  removeUserDrawingOverlaysFromChart()
  activeDrawingTool.value = null
  showMessage('info', '已清除当前股票的所有画线')
}

const periodLabel = (p) => {
  const map = {
    '1d': '日线', '3d': '3日线', '5d': '5日线',
    'week': '周线', 'month': '月线', 'year': '年线',
    '15m': '15分钟', '30m': '30分钟', '60m': '60分钟', '120m': '120分钟'
  }
  return map[p] || p
}

// ============ 训练状态 ============
const stockCode = ref('')
const stockName = ref('')
const sessionData = ref(null)
const trainCursor = ref(0)
const phase = ref('open')
const position = ref(null)
const pendingOrder = ref(null)
const trades = ref([])
const tradeLogRef = ref(null)
const autoMode = ref(false)
const message = ref('')
const messageType = ref('info')
const showSettings = ref(false)
const loading = ref(false)
const showSaveDialog = ref(false)
const showImportDialog = ref(false)
const showVipDialog = ref(false)
const saveGroupIds = ref([])
const saveGroupInput = ref('')
const selectedSaveGroupId = ref('')
const savingRun = ref(false)
const importGroupIds = ref([])
const selectedImportGroupId = ref('')
const importRuns = ref([])
const selectedImportedRun = ref(null)
const importLoading = ref(false)
const vipStatus = ref(null)
const vipSaving = ref(false)

let autoTimer = null

// ============ 画线工具 ============
const DRAWING_STORAGE_KEY = 'kline_trainer_user_drawings_v1'
const USER_DRAWING_GROUP = 'trainer_user_drawings'
const activeDrawingTool = ref(null)

const drawingTools = [
  { name: 'segment', label: '线段', icon: 'fas fa-minus' },
  { name: 'straightLine', label: '直线', icon: 'fas fa-grip-lines' },
  { name: 'horizontalStraightLine', label: '水平线', icon: 'fas fa-arrows-alt-h' },
  { name: 'rayLine', label: '射线', icon: 'fas fa-long-arrow-alt-right' },
  { name: 'fibonacciLine', label: '斐波那契', icon: 'fas fa-chart-line' },
  { name: 'parallelStraightLine', label: '平行线', icon: 'fas fa-align-justify' },
  { name: 'amplitudeRuler', label: '幅度尺', icon: 'fas fa-ruler-combined' },
  { name: 'rectangle', label: '矩形框', icon: 'fas fa-vector-square' },
]

// ============ 计算属性 ============
const trainCount = computed(() => sessionData.value ? sessionData.value.train_count : 0)
const isDone = computed(() => sessionData.value && trainCursor.value >= trainCount.value)

const advanceLabel = computed(() => {
  if (!sessionData.value) return '加载中...'
  if (isDone.value) return '已完成'
  if (isTwoStep.value) {
    return phase.value === 'open' ? '展示收盘价' : '展示下一根开盘价'
  }
  return '下一根K线'
})

const isTwoStep = computed(() => config.advanceMode === 'two_step')
const isOneStep = computed(() => config.advanceMode === 'one_step')
const canAct = computed(() => sessionData.value && !isDone.value)
const DEFAULT_MAX_FREE_TRAININGS = 66
const DEFAULT_VIP_PAYMENT_URL = 'https://qr.alipay.com/tsx11426f7zf2uqre8gdoe0'
const isVip = computed(() => !!vipStatus.value?.is_vip)
const trainingCount = computed(() => Number(vipStatus.value?.training_count ?? 0))
const maxFreeTrainings = computed(() => Number(vipStatus.value?.max_free_trainings ?? DEFAULT_MAX_FREE_TRAININGS))
const remainingFree = computed(() => Number(vipStatus.value?.remaining_free ?? maxFreeTrainings.value))
const vipPaymentUrl = computed(() => vipStatus.value?.payment_url || DEFAULT_VIP_PAYMENT_URL)
const vipButtonText = computed(() => {
  if (!vipStatus.value) return '支持/VIP'
  if (isVip.value) return '支持者'
  return `剩余 ${remainingFree.value}/${maxFreeTrainings.value}`
})
const vipButtonClass = computed(() => [
  'btn',
  isVip.value ? 'btn-warning' : remainingFree.value <= 5 ? 'btn-danger' : 'btn-secondary',
  'vip-status-chip'
])

// ── 当前K线统计 ──
const currentBar = computed(() => getCurrentTrainBar())

const prevClose = computed(() => {
  const bar = currentBar.value
  if (!bar || !sessionData.value) return null
  const dailyBars = sessionData.value.bars['1d']
  if (!dailyBars) return null
  // 找当前 bar 在 train 中的位置，前一根可能是 train 中前一根或 display 最后一根
  const idx = dailyBars.train?.findIndex(b => b.date === bar.date)
  if (idx !== undefined && idx > 0) return dailyBars.train[idx - 1].close
  if (idx === 0 && dailyBars.display?.length) {
    return dailyBars.display[dailyBars.display.length - 1].close
  }
  return null
})

const barChange = computed(() => {
  if (!currentBar.value || !prevClose.value) return null
  return currentBar.value.close - prevClose.value
})

const barChangePct = computed(() => {
  if (barChange.value === null || !prevClose.value) return null
  return (barChange.value / prevClose.value) * 100
})

const barAmplitude = computed(() => {
  if (!currentBar.value || !prevClose.value) return null
  return ((currentBar.value.high - currentBar.value.low) / prevClose.value) * 100
})

const currentPnl = computed(() => {
  if (!position.value || !sessionData.value) return 0
  const currentBar = getCurrentTrainBar()
  if (!currentBar) return 0
  const currentPrice = isTwoStep.value && phase.value === 'open'
    ? currentBar.open
    : currentBar.close
  return ((currentPrice - position.value.entryPrice) / position.value.entryPrice) * 100
})

const totalPnl = computed(() => trades.value.reduce((sum, t) => sum + (t.pnlPercent || 0), 0))
const cumulativePnl = computed(() => {
  const closed = trades.value.filter(t => t.pnlPercent != null)
  if (!closed.length) return 0
  let factor = 1
  for (const t of closed) {
    factor *= (1 + (t.pnlPercent || 0) / 100)
  }
  return (factor - 1) * 100
})
const tradeCount = computed(() => trades.value.filter(t => t.pnlPercent != null).length)
const winRate = computed(() => {
  const closed = trades.value.filter(t => t.pnlPercent != null)
  if (!closed.length) return 0
  return Math.round(closed.filter(t => (t.pnlPercent || 0) > 0).length / closed.length * 100)
})
const saveTargetGroupId = computed(() => saveGroupInput.value.trim() || selectedSaveGroupId.value)

// ============ 获取当前训练K线 ============
const getCurrentTrainBar = () => {
  if (!sessionData.value) return null
  const dailyBars = sessionData.value.bars['1d']
  if (!dailyBars || !dailyBars.train || trainCursor.value >= dailyBars.train.length) return null
  return dailyBars.train[trainCursor.value]
}

const getPeriodBars = () => {
  if (!sessionData.value || !sessionData.value.bars) return null
  return sessionData.value.bars[currentPeriod.value] || sessionData.value.bars['1d'] || null
}

// ============ 图表数据 ============
const buildChartData = () => {
  const periodData = getPeriodBars()
  if (!periodData) return []

  // 日线 train 数据，用于确定分界日期
  const dailyBars = sessionData.value.bars['1d']
  const dailyTrain = dailyBars?.train || []

  // 分界日 = 第 trainCursor 根日线 train bar 的日期（已揭示的最后一根）
  const boundaryDate = trainCursor.value > 0 && trainCursor.value <= dailyTrain.length
    ? dailyTrain[trainCursor.value - 1].date
    : null

  // 当前日线 bar 的日期（用于两步模式下部分揭示）
  const currentDailyDate = trainCursor.value < dailyTrain.length
    ? dailyTrain[trainCursor.value].date
    : null

  const allBars = [...periodData.display]
  const trainBars = periodData.train || []

  for (let i = 0; i < trainBars.length; i++) {
    const barDate = trainBars[i].date
    if (boundaryDate && barDate <= boundaryDate) {
      // 已揭示：日期 <= 分界日
      allBars.push(trainBars[i])
    } else if (barDate === currentDailyDate) {
      // 当前 bar：日期匹配当前日线 bar
      const bar = { ...trainBars[i] }
      if (isTwoStep.value && phase.value === 'open') {
        bar.high = bar.open
        bar.low = bar.open
        bar.close = bar.open
      }
      allBars.push(bar)
    }
  }

  return allBars.map(item => ({
    timestamp: new Date(item.date).getTime(),
    open: Number(item.open),
    high: Number(item.high),
    low: Number(item.low),
    close: Number(item.close),
    volume: Number(item.volume || 0)
  }))
}

const updateChart = () => {
  if (!chart) return
  const periodData = getPeriodBars()
  if (!periodData) return
  const data = buildChartData()
  const totalBars = periodData.display.length + (periodData.train?.length || 0)
  const visibleCount = data.length

  // 先用近似 barSpace 避免首次渲染过宽
  if (chartRef.value && totalBars > 0) {
    chart.setBarSpace(Math.max(1, chartRef.value.clientWidth / totalBars))
  }

  // onDataReady 时布局已完成，用精确尺寸修正 barSpace 和偏移量
  const onReady = () => {
    chart.unsubscribeAction('onDataReady', onReady)
    const candleSize = chart.getSize('candle_pane', 'main')
    if (candleSize && candleSize.width > 0 && totalBars > 0) {
      const barSpace = candleSize.width / totalBars
      chart.setBarSpace(barSpace)
      const rightOffset = Math.max(0, (totalBars - visibleCount) * barSpace)
      chart.setMaxOffsetRightDistance(candleSize.width)
      chart.setMaxOffsetLeftDistance(0)
      chart.setOffsetRightDistance(rightOffset, true)
    }
    if (config.showMarkers) {
      rebuildTradeMarkers()
    }
    rebuildUserDrawingOverlays()
    ensureIndicators()
  }
  chart.subscribeAction('onDataReady', onReady)
  chart.applyNewData(data, false)

  if (!config.showMarkers) {
    chart.removeOverlay({ groupId: 'trade_marks' })
  }
}

// ============ 交易标记 ============
const rebuildTradeMarkers = () => {
  if (!chart || !sessionData.value) return
  chart.removeOverlay({ groupId: 'trade_marks' })

  // 仅在日线级别标注买卖点，小级别忽略
  if (currentPeriod.value !== '1d') return

  const periodData = getPeriodBars()
  if (!periodData) return
  const displayCount = periodData.display.length
  const allBars = [...periodData.display, ...periodData.train]
  const overlays = []

  trades.value.forEach((t, i) => {
    const barIdx = displayCount + t.barIndex
    const bar = allBars[barIdx]
    if (!bar) return
    const ts = new Date(bar.date).getTime()
    overlays.push({
      name: 'simpleAnnotation',
      groupId: 'trade_marks',
      points: [{ timestamp: ts, value: t.price }],
      extendData: t.side === 'buy' ? 'B' : 'S',
      styles: {
        line: { color: t.side === 'buy' ? config.lineColors.buyMarker : config.lineColors.sellMarker },
        text: { color: t.side === 'buy' ? config.lineColors.buyMarker : config.lineColors.sellMarker }
      }
    })
  })

  if (overlays.length) {
    chart.createOverlay(overlays)
  }
}

// ============ 推进逻辑 ============
const advance = () => {
  if (!sessionData.value || isDone.value) return
  if (isTwoStep.value) {
    twoStepWait()
  } else {
    oneStepWait()
  }
}

const advanceToNextPhase = () => {
  if (isTwoStep.value) {
    if (phase.value === 'open') {
      phase.value = 'close'
    } else {
      trainCursor.value++
      phase.value = 'open'
      processPendingOrder()
    }
  } else {
    trainCursor.value++
    processPendingOrder()
  }
  updateChart()

  if (isDone.value) {
    stopAuto()
    settlePosition()
    showMessage('success', '训练结束！')
  }
}

const processPendingOrder = () => {
  if (!pendingOrder.value) return
  const order = pendingOrder.value
  pendingOrder.value = null

  const bar = getCurrentTrainBar()
  if (!bar) return

  if (order.side === 'buy') {
    if (position.value) return
    position.value = {
      entryPrice: bar.open,
      entryBar: trainCursor.value
    }
    trades.value.push({
      side: 'buy',
      price: bar.open,
      barIndex: trainCursor.value,
      phase: 'open',
      tradeType: 'auction',
      exitPrice: null,
      pnlPercent: null
    })
    showMessage('info', `竞价买入 @ ${bar.open.toFixed(2)}`)
  } else {
    if (!position.value) return
    if (position.value.entryBar === trainCursor.value) return
    const pnl = ((bar.open - position.value.entryPrice) / position.value.entryPrice) * 100
    trades.value.push({
      side: 'sell',
      price: bar.open,
      barIndex: trainCursor.value,
      phase: 'open',
      tradeType: 'auction',
      pnlPercent: pnl
    })
    position.value = null
    showMessage(pnl >= 0 ? 'success' : 'error', `竞价卖出 @ ${bar.open.toFixed(2)}  盈亏: ${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}%`)
  }
}

const settlePosition = () => {
  pendingOrder.value = null
  if (!position.value || !sessionData.value) return
  const dailyBars = sessionData.value.bars['1d']
  if (!dailyBars || !dailyBars.train || !dailyBars.train.length) return
  const lastBar = dailyBars.train[dailyBars.train.length - 1]
  if (!lastBar) return
  const exitPrice = lastBar.close
  const pnl = ((exitPrice - position.value.entryPrice) / position.value.entryPrice) * 100
  trades.value.push({
    side: 'sell',
    price: exitPrice,
    barIndex: dailyBars.train.length - 1,
    phase: 'close',
    tradeType: 'settle',
    pnlPercent: pnl
  })
  position.value = null
}

// ============ 自动推进 ============
const toggleAuto = () => {
  autoMode.value = !autoMode.value
  if (autoMode.value) {
    startAuto()
  } else {
    stopAuto()
  }
}

const startAuto = () => {
  stopAuto()
  const interval = (config.autoInterval || 2) * 1000
  autoTimer = setInterval(() => {
    if (isDone.value) {
      stopAuto()
      return
    }
    if (isTwoStep.value) {
      twoStepWait()
    } else {
      oneStepWait()
    }
  }, interval)
}

const stopAuto = () => {
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = null
  }
}

// ============ 交易逻辑 ============
// -- 两步模式 --
const twoStepBuy = () => {
  if (!canAct.value) return
  const bar = getCurrentTrainBar()
  if (!bar) return
  if (position.value) {
    showMessage('info', '已持仓，请先卖出')
    return
  }
  const price = phase.value === 'open' ? bar.open : bar.close
  const tradeType = phase.value === 'open' ? 'open_buy' : 'close_buy'
  position.value = {
    entryPrice: price,
    entryBar: trainCursor.value
  }
  trades.value.push({
    side: 'buy',
    price,
    barIndex: trainCursor.value,
    phase: phase.value,
    tradeType,
    exitPrice: null,
    pnlPercent: null
  })
  showMessage('info', `${phase.value === 'open' ? '开盘买入' : '收盘买入'} @ ${price.toFixed(2)}`)
  advanceToNextPhase()
}

const twoStepSell = () => {
  if (!canAct.value) return
  const bar = getCurrentTrainBar()
  if (!bar) return
  if (!position.value) {
    showMessage('info', '未持仓，无法卖出')
    return
  }
  if (position.value.entryBar === trainCursor.value) {
    showMessage('info', 'T+1: 同一根K线不能买卖')
    return
  }
  const price = phase.value === 'open' ? bar.open : bar.close
  const tradeType = phase.value === 'open' ? 'open_sell' : 'close_sell'
  const pnl = ((price - position.value.entryPrice) / position.value.entryPrice) * 100
  trades.value.push({
    side: 'sell',
    price,
    barIndex: trainCursor.value,
    phase: phase.value,
    tradeType,
    pnlPercent: pnl
  })
  position.value = null
  showMessage(pnl >= 0 ? 'success' : 'error', `${phase.value === 'open' ? '开盘卖出' : '收盘卖出'} @ ${price.toFixed(2)}  盈亏: ${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}%`)
  advanceToNextPhase()
}

const twoStepWait = () => {
  if (!canAct.value) return
  advanceToNextPhase()
}

// -- 一步模式 --
const auctionBuy = () => {
  if (!canAct.value) return
  if (position.value) {
    showMessage('info', '已持仓，请先卖出')
    return
  }
  pendingOrder.value = { side: 'buy' }
  showMessage('info', '挂单: 竞价买入 (下一根开盘价成交)')
  advanceToNextPhase()
}

const auctionSell = () => {
  if (!canAct.value) return
  if (!position.value) {
    showMessage('info', '未持仓，无法卖出')
    return
  }
  if (position.value.entryBar === trainCursor.value) {
    showMessage('info', 'T+1: 同一根K线不能买卖')
    return
  }
  pendingOrder.value = { side: 'sell' }
  showMessage('info', '挂单: 竞价卖出 (下一根开盘价成交)')
  advanceToNextPhase()
}

const closeBuy = () => {
  if (!canAct.value) return
  const bar = getCurrentTrainBar()
  if (!bar) return
  if (position.value) {
    showMessage('info', '已持仓，请先卖出')
    return
  }
  const price = bar.close
  position.value = {
    entryPrice: price,
    entryBar: trainCursor.value
  }
  trades.value.push({
    side: 'buy',
    price,
    barIndex: trainCursor.value,
    phase: 'close',
    tradeType: 'close_buy',
    exitPrice: null,
    pnlPercent: null
  })
  showMessage('info', `收盘买入 @ ${price.toFixed(2)}`)
  advanceToNextPhase()
}

const closeSell = () => {
  if (!canAct.value) return
  const bar = getCurrentTrainBar()
  if (!bar) return
  if (!position.value) {
    showMessage('info', '未持仓，无法卖出')
    return
  }
  if (position.value.entryBar === trainCursor.value) {
    showMessage('info', 'T+1: 同一根K线不能买卖')
    return
  }
  const price = bar.close
  const pnl = ((price - position.value.entryPrice) / position.value.entryPrice) * 100
  trades.value.push({
    side: 'sell',
    price,
    barIndex: trainCursor.value,
    phase: 'close',
    tradeType: 'close_sell',
    pnlPercent: pnl
  })
  position.value = null
  showMessage(pnl >= 0 ? 'success' : 'error', `收盘卖出 @ ${price.toFixed(2)}  盈亏: ${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}%`)
  advanceToNextPhase()
}

const oneStepWait = () => {
  if (!canAct.value) return
  advanceToNextPhase()
}

// ============ 周期切换 ============
const switchPeriod = (p) => {
  if (!periodHasData(p)) return
  currentPeriod.value = p
  nextTick(() => updateChart())
}

// ============ 显示消息 ============
const showMessage = (type, text) => {
  messageType.value = type
  message.value = text
  setTimeout(() => { message.value = '' }, 3000)
}

const formatTradeTime = (t) => {
  const bars = getPeriodBars()
  if (!bars || !bars.train || !bars.train[t.barIndex]) return ''
  const date = bars.train[t.barIndex].date
  const typeLabel = t.tradeType === 'auction' ? '竞价' : t.tradeType === 'settle' ? '结算' : t.phase === 'open' ? '开盘' : '收盘'
  return `${date} ${typeLabel}`
}

const tradeTypeLabel = (t) => {
  const prefix = t.tradeType === 'auction' ? '竞价' : t.tradeType === 'settle' ? '结算' : t.phase === 'open' ? '开盘' : '收盘'
  return `${prefix}${t.side === 'buy' ? '买入' : '卖出'}`
}

const closedTradeNumber = (tradeIndex) => trades.value.slice(0, tradeIndex + 1)
  .filter(t => t.pnlPercent != null).length

const formatPercent = (value) => {
  const num = Number(value || 0)
  return `${num >= 0 ? '+' : ''}${num.toFixed(2)}%`
}

const advanceModeLabel = (mode) => mode === 'one_step' ? '一步' : '两步'

// 交易记录自动滚到底部
watch(() => trades.value.length, () => {
  nextTick(() => {
    const el = tradeLogRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
})

// ============ 训练记录保存/导入 ============
const toPlain = (value, fallback) => {
  if (value == null) return fallback
  try {
    return JSON.parse(JSON.stringify(value))
  } catch {
    return fallback
  }
}

const loadRecordGroupIds = async () => {
  const res = await trainerApi.getSaveIds()
  if (res.success) {
    const ids = Array.isArray(res.ids) ? res.ids : []
    saveGroupIds.value = ids
    importGroupIds.value = ids
    if (!selectedSaveGroupId.value && ids.length) {
      selectedSaveGroupId.value = ids[0]
    }
  }
}

const buildTrainingSnapshot = () => ({
  savedAt: new Date().toISOString(),
  stockCode: stockCode.value,
  stockName: stockName.value,
  totalBars: sessionData.value?.total_bars || 0,
  displayCount: sessionData.value?.display_count || 0,
  trainCount: sessionData.value?.train_count || 0,
  currentPeriod: currentPeriod.value,
  trainCursor: trainCursor.value,
  phase: phase.value,
  isDone: !!isDone.value,
  advanceMode: config.advanceMode,
  config: toPlain(config, {}),
  position: toPlain(position.value, null),
  pendingOrder: toPlain(pendingOrder.value, null),
  trades: trades.value.map(t => ({ ...t })),
  stats: {
    totalPnl: totalPnl.value,
    cumulativePnl: cumulativePnl.value,
    tradeCount: tradeCount.value,
    winRate: winRate.value
  },
  bars: toPlain(sessionData.value?.bars, {}),
  session: toPlain(sessionData.value, {})
})

const openSaveDialog = async () => {
  if (!sessionData.value) {
    showMessage('info', '请先加载一局训练')
    return
  }
  saveGroupInput.value = ''
  showSaveDialog.value = true
  try {
    await loadRecordGroupIds()
  } catch (e) {
    showMessage('error', '加载保存ID失败: ' + e.message)
  }
}

const closeSaveDialog = () => {
  if (savingRun.value) return
  showSaveDialog.value = false
}

const saveCurrentRun = async () => {
  const groupId = saveTargetGroupId.value.trim()
  if (!groupId) {
    showMessage('info', '请填写或选择保存ID')
    return
  }
  savingRun.value = true
  try {
    const res = await trainerApi.saveRun(groupId, buildTrainingSnapshot())
    if (res.success) {
      selectedSaveGroupId.value = groupId
      showSaveDialog.value = false
      await loadRecordGroupIds()
      showMessage('success', `训练记录已保存到 ${groupId}`)
    } else {
      showMessage('error', res.message || '保存训练记录失败')
    }
  } catch (e) {
    showMessage('error', '保存训练记录失败: ' + e.message)
  } finally {
    savingRun.value = false
  }
}

const openImportDialog = async () => {
  showImportDialog.value = true
  selectedImportedRun.value = null
  selectedImportGroupId.value = ''
  importRuns.value = []
  importLoading.value = true
  try {
    await loadRecordGroupIds()
  } catch (e) {
    showMessage('error', '加载保存ID失败: ' + e.message)
  } finally {
    importLoading.value = false
  }
}

const closeImportDialog = () => {
  if (importLoading.value) return
  showImportDialog.value = false
}

const applyVipStatus = (status) => {
  if (status && typeof status === 'object') {
    vipStatus.value = status
  }
}

const loadVipStatus = async () => {
  try {
    const res = await trainerApi.getVip()
    if (res.success) {
      applyVipStatus(res.vip)
    }
  } catch (e) {
    console.warn('加载VIP状态失败:', e)
  }
}

const openVipDialog = async () => {
  showVipDialog.value = true
  await loadVipStatus()
}

const closeVipDialog = () => {
  if (vipSaving.value) return
  showVipDialog.value = false
}

const setVipMode = async (value) => {
  vipSaving.value = true
  try {
    const res = await trainerApi.saveVip({ is_vip: value })
    if (res.success) {
      applyVipStatus(res.vip)
      showMessage('success', value ? '支持者模式已开启' : '支持者模式已关闭')
    } else {
      showMessage('error', res.message || '保存VIP设置失败')
      await loadVipStatus()
    }
  } catch (e) {
    showMessage('error', '保存VIP设置失败: ' + e.message)
    await loadVipStatus()
  } finally {
    vipSaving.value = false
  }
}

const openPaymentPage = () => {
  window.open(vipPaymentUrl.value, '_blank', 'noopener')
}

const loadImportRuns = async () => {
  const groupId = selectedImportGroupId.value
  selectedImportedRun.value = null
  importRuns.value = []
  if (!groupId) return
  importLoading.value = true
  try {
    const res = await trainerApi.listRuns(groupId)
    if (res.success) {
      importRuns.value = Array.isArray(res.runs) ? res.runs : []
    } else {
      showMessage('error', res.message || '加载训练记录失败')
    }
  } catch (e) {
    showMessage('error', '加载训练记录失败: ' + e.message)
  } finally {
    importLoading.value = false
  }
}

const openImportedRun = async (run) => {
  if (!run?.id) return
  importLoading.value = true
  try {
    const res = await trainerApi.getRun(run.id)
    if (res.success && res.record) {
      selectedImportedRun.value = res.record
    } else {
      showMessage('error', res.message || '加载训练详情失败')
    }
  } catch (e) {
    showMessage('error', '加载训练详情失败: ' + e.message)
  } finally {
    importLoading.value = false
  }
}

const backToImportList = () => {
  selectedImportedRun.value = null
}

const importedRunTrades = (run) => {
  const list = run?.payload?.trades
  return Array.isArray(list) ? list : []
}

const formatImportedTradeTime = (t, run) => {
  const bars = run?.payload?.bars?.['1d'] || run?.payload?.session?.bars?.['1d']
  const date = bars?.train?.[t.barIndex]?.date || ''
  const typeLabel = t.tradeType === 'auction' ? '竞价' : t.tradeType === 'settle' ? '结算' : t.phase === 'open' ? '开盘' : '收盘'
  return `${date} ${typeLabel}`.trim()
}

// ============ 下一轮 ============
const nextRound = async () => {
  stopAuto()
  autoMode.value = false
  position.value = null
  pendingOrder.value = null
  trades.value = []
  trainCursor.value = 0
  phase.value = 'open'
  sessionData.value = null
  await loadSession()
}

// ============ 数据加载 ============
const loadSession = async () => {
  loading.value = true
  try {
    const res = await trainerApi.startSession()
    applyVipStatus(res.vip)
    if (res.success) {
      stockCode.value = res.stock_code
      stockName.value = res.stock_name
      sessionData.value = res
      trainCursor.value = 0
      phase.value = 'open'
      ensureCurrentPeriodVisible()
      nextTick(() => updateChart())
      showMessage('success', `加载成功: ${res.stock_code} ${res.stock_name} 展示${res.display_count}根 训练${res.train_count}根`)
    } else {
      showMessage('error', res.message || '加载失败')
      if (res.vip && !res.vip.can_train) {
        showVipDialog.value = true
      }
    }
  } catch (e) {
    applyVipStatus(e?.response?.data?.vip)
    showMessage('error', '请求失败: ' + e.message)
    if (e?.response?.data?.vip && !e.response.data.vip.can_train) {
      showVipDialog.value = true
    }
  } finally {
    loading.value = false
  }
}

// ============ 设置 ============
const loadConfig = async () => {
  try {
    const res = await trainerApi.getConfig()
    if (res.success && res.config) {
      Object.assign(config, res.config)
      config.blindCode = config.blindCode || false
      config.blindName = config.blindName || false
      config.blindTime = config.blindTime || false
      config.showMarkers = config.showMarkers !== false
    }
  } catch (e) {
    console.warn('加载训练配置失败:', e)
  }
}

const saveSettings = async () => {
  try {
    if (config.displayCount + config.trainCount > config.totalBars) {
      config.totalBars = config.displayCount + config.trainCount
    }
    const payload = { ...config }
    delete payload.shortcuts
    delete payload.blindCode
    delete payload.blindName
    delete payload.blindTime
    delete payload.showMarkers
    await trainerApi.saveConfig(payload)
    localStorage.setItem('kline_trainer_ui_config', JSON.stringify({
      shortcuts: config.shortcuts,
      blindCode: config.blindCode,
      blindName: config.blindName,
      blindTime: config.blindTime,
      showMarkers: config.showMarkers,
      lineColors: config.lineColors,
      drawingColors: config.drawingColors
    }))
    showSettings.value = false
    applyChartColors()
    rebuildTradeMarkers()
    rebuildUserDrawingOverlays()
    showMessage('success', '设置已保存')
    nextTick(() => updateChart())
  } catch (e) {
    showMessage('error', '保存失败: ' + e.message)
  }
}

const closeSettings = () => {
  showSettings.value = false
}

const loadUiConfig = () => {
  try {
    const raw = localStorage.getItem('kline_trainer_ui_config')
    if (raw) {
      const ui = JSON.parse(raw)
      if (ui.shortcuts) Object.assign(config.shortcuts, ui.shortcuts)
      if (ui.blindCode !== undefined) config.blindCode = ui.blindCode
      if (ui.blindName !== undefined) config.blindName = ui.blindName
      if (ui.blindTime !== undefined) config.blindTime = ui.blindTime
      if (ui.showMarkers !== undefined) config.showMarkers = ui.showMarkers
      if (ui.lineColors) Object.assign(config.lineColors, ui.lineColors)
      if (ui.drawingColors) Object.assign(config.drawingColors, ui.drawingColors)
    }
  } catch (e) {
    console.warn('加载UI配置失败:', e)
  }
}

// ============ 图表初始化 ============
const initChart = () => {
  if (!chartRef.value) return

  chart = init(chartRef.value, {
    locale: 'zh-CN',
    customApi: {
      formatBigNumber: (value) => value.toString(),
      formatDate: (dateTimeFormat, timestamp) => {
        if (config.blindTime) return ''
        const d = new Date(timestamp)
        const y = d.getFullYear()
        const mo = String(d.getMonth() + 1).padStart(2, '0')
        const day = String(d.getDate()).padStart(2, '0')
        return `${y}-${mo}-${day}`
      }
    },
    styles: {
      candle: {
        bar: {
          upColor: config.lineColors.up,
          downColor: config.lineColors.down,
          noChangeColor: config.lineColors.noChange,
          upBorderColor: config.lineColors.up,
          downBorderColor: config.lineColors.down,
          noChangeBorderColor: config.lineColors.noChange,
          upWickColor: config.lineColors.up,
          downWickColor: config.lineColors.down,
          noChangeWickColor: config.lineColors.noChange
        },
        priceMark: {
          high: { show: true, color: config.lineColors.up },
          low: { show: true, color: config.lineColors.down },
          last: { show: true }
        }
      },
      indicator: {
        ohlc: {
          upColor: config.lineColors.up,
          downColor: config.lineColors.down,
          noChangeColor: config.lineColors.noChange
        },
        bars: [{
          upColor: config.lineColors.up,
          downColor: config.lineColors.down,
          noChangeColor: config.lineColors.noChange
        }]
      }
    }
  })

  chart.setPaneOptions({
    id: 'candle_pane',
    gap: { top: 0.15, bottom: 0.15 },
    axisOptions: { scrollZoomEnabled: false }
  })
}

const handleResize = () => {
  if (!chart) return
  chart.resize()
  // 重新计算 barSpace 和偏移量
  const periodData = getPeriodBars()
  if (!periodData) return
  const totalBars = periodData.display.length + (periodData.train?.length || 0)
  const data = chart.getDataList?.() || []
  const visibleCount = data.length
  if (totalBars === 0 || visibleCount === 0) return
  let candleWidth = 0
  const candleSize = chart.getSize('candle_pane', 'main')
  if (candleSize && candleSize.width > 0) {
    candleWidth = candleSize.width
  } else if (chartRef.value) {
    candleWidth = chartRef.value.clientWidth - 60
  }
  if (candleWidth > 0) {
    const barSpace = candleWidth / totalBars
    chart.setBarSpace(barSpace)
    const rightOffset = Math.max(0, (totalBars - visibleCount) * barSpace)
    chart.setMaxOffsetRightDistance(candleWidth)
    chart.setOffsetRightDistance(rightOffset, true)
  }
}

// ============ 生命周期 ============
// ============ 键盘快捷键 ============
const handleKeydown = (e) => {
  if (!canAct.value || showSettings.value || showSaveDialog.value || showImportDialog.value || showVipDialog.value) return
  // 忽略在输入框内的按键
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return
  const key = e.key
  const sc = config.shortcuts
  if (isTwoStep.value) {
    if (key === sc.two_buy) twoStepBuy()
    else if (key === sc.two_sell) twoStepSell()
    else if (key === sc.two_wait) twoStepWait()
  } else {
    if (key === sc.one_auctionBuy) auctionBuy()
    else if (key === sc.one_auctionSell) auctionSell()
    else if (key === sc.one_closeBuy) closeBuy()
    else if (key === sc.one_closeSell) closeSell()
    else if (key === sc.one_wait) oneStepWait()
  }
}

// ============ 生命周期 ============
onMounted(async () => {
  await loadConfig()
  loadUiConfig()
  await loadVipStatus()
  initChart()
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleKeydown)
  await loadSession()
})

onUnmounted(() => {
  stopAuto()
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleKeydown)
  if (chart) {
    dispose(chart)
    chart = null
  }
})
</script>

<style scoped>
.trainer-page {
  --primary-color: #134cff;
  --primary-hover: #0746ff;
  --main-bg: #f5f7fa;
  --card-bg: #ffffff;
  --text-primary: #303133;
  --text-secondary: #909399;
  --text-muted: #c0c4cc;
  --border-color: #ebeef5;
  --bg-primary: #ffffff;
  --bg-secondary: #f8fafc;
  --bg-hover: #eef2f7;
  --success-color: #10b981;
  --danger-color: #ef4444;
  --warning-color: #f59e0b;
  --card-shadow: 0 4px 16px 0 rgba(0,0,0,.08);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  gap: 6px;
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
}

.card {
  background: var(--card-bg);
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: var(--card-shadow);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 7px 14px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.2;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s, transform 0.15s;
  white-space: nowrap;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn:not(:disabled):hover {
  transform: translateY(-1px);
}

.btn-primary {
  background: linear-gradient(356deg, #134cff 0%, #8337ff 103%);
  color: #fff;
}

.btn-primary:not(:disabled):hover {
  background: linear-gradient(356deg, #0746ff 0%, #6c2fff 103%);
}

.btn-success {
  background: var(--success-color);
  color: #fff;
}

.btn-success:not(:disabled):hover {
  background: #059669;
}

.btn-warning {
  background: var(--warning-color);
  color: #fff;
}

.btn-warning:not(:disabled):hover {
  background: #d97706;
}

.btn-danger {
  background: var(--danger-color);
  color: #fff;
}

.btn-danger:not(:disabled):hover {
  background: #dc2626;
}

.btn-secondary {
  background: var(--main-bg);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-secondary:not(:disabled):hover {
  background: #e8e9eb;
  border-color: var(--text-muted);
}

.control-panel { margin-bottom: 0; }
.control-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.info-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.info-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.info-value.up { color: #ef4444; }
.info-value.down { color: #10b981; }
.bar-stats .info-group { min-width: 70px; }

.phase-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 4px;
}
.phase-tag.open { background: #dbeafe; color: #2563eb; }
.phase-tag.close { background: #dcfce7; color: #16a34a; }

/* 周期标签页 */
.period-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 12px;
  position: relative;
}

.period-tab {
  padding: 6px 14px;
  font-size: 13px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.period-tab:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.period-tab.active {
  background: var(--primary-color, #3b82f6);
  color: #fff;
}

.period-tab.more {
  border: 1px dashed var(--border-color);
}

.period-more {
  position: relative;
}

.period-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 100;
  display: flex;
  flex-direction: column;
  padding: 4px;
  min-width: 120px;
}

.period-dropdown .period-tab {
  text-align: left;
  width: 100%;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.toolbar-section {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-label {
  font-size: 12px;
  color: var(--text-secondary, #999);
  margin-right: 4px;
  white-space: nowrap;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--border-color, #e0e0e0);
  margin: 0 8px;
}

.toolbar-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--border-color, #d0d0d0);
  border-radius: 4px;
  background: var(--bg-secondary, #f8f8f8);
  color: var(--text-primary, #333);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.toolbar-btn:hover {
  background: var(--bg-hover, #e8e8e8);
  border-color: var(--primary-color, #3b82f6);
}

.toolbar-btn.active {
  background: var(--primary-color, #3b82f6);
  border-color: var(--primary-color, #3b82f6);
  color: #fff;
}

.toolbar-btn.danger {
  color: #ef4444;
  border-color: #ef4444;
}

.toolbar-btn.danger:hover {
  background: #ef4444;
  color: #fff;
}

.toolbar-btn i {
  margin-right: 3px;
}

/* 全局控制按钮行 */
.global-actions {
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px solid var(--border-color);
}

/* 主区域：K线图 + 右侧面板 */
.main-area {
  display: flex;
  flex: 1;
  gap: 6px;
  min-height: 0;
}

/* 图表容器 */
.chart-container {
  padding: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  min-width: 0;
}

#trainer-chart {
  width: 100%;
  flex: 1;
  min-height: 400px;
}

/* 右侧交易面板 */
.right-panel {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
}

.panel-card {
  padding: 10px 12px;
}

/* 消息提示toast */
.toast-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  flex-shrink: 0;
}
.toast-msg span {
  flex: 1;
  min-width: 0;
}
.alert-info { background: #dbeafe; color: #1e40af; }
.alert-success { background: #d1fae5; color: #065f46; }
.alert-error { background: #fee2e2; color: #991b1b; }
.alert-warning { background: #fef3c7; color: #92400e; }

/* 右侧交易按钮 */
.trade-actions-card {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}
.trade-actions-card .trade-btn {
  width: 100%;
  min-width: 0;
  padding: 8px 6px;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
}
.trade-actions-card .trade-btn.wait {
  grid-column: 1 / -1;
}
.trade-btn-text {
  white-space: nowrap;
}

/* 交易栏 */
.trade-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
}

.trade-buttons {
  display: flex;
  gap: 8px;
}

.trade-btn {
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  color: #fff;
}

.trade-btn.buy {
  background: #ef4444;
}
.trade-btn.buy:hover {
  background: #dc2626;
}

.trade-btn.sell {
  background: #10b981;
}
.trade-btn.sell:hover {
  background: #059669;
}

.trade-btn.wait {
  background: #6b7280;
}
.trade-btn.wait:hover {
  background: #4b5563;
}

.trade-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.position-info {
  flex: 1;
  text-align: right;
}

.position-badge {
  font-size: 14px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 4px;
}

.position-badge.long {
  background: #fef2f2;
  color: #dc2626;
}

.position-badge.empty {
  color: var(--text-secondary);
}

.pending-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: #fef3c7;
  color: #d97706;
  margin-left: 8px;
}

.pnl-positive { color: #ef4444; }
.pnl-negative { color: #10b981; }

.key-hint {
  display: inline-block;
  margin-left: 3px;
  padding: 0 4px;
  font-size: 10px;
  background: rgba(255,255,255,0.25);
  border-radius: 3px;
  line-height: 16px;
  min-width: 16px;
  text-align: center;
}

/* 内联持仓信息 (控制栏内) */
.position-info-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  white-space: nowrap;
}
.position-info-inline .position-badge {
  font-size: 12px;
  padding: 2px 8px;
}
.position-info-inline .pending-badge {
  font-size: 11px;
  color: #f59e0b;
  background: rgba(245,158,11,0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

/* 交易记录 */
.trade-log {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.log-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--text-primary);
}

.log-stats-list {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color, rgba(128,128,128,0.15));
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 3px 0;
}

.stat-row.main {
  padding: 4px 0 8px 0;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--border-color, rgba(128,128,128,0.15));
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-row.main .stat-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-val {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-row.main .stat-val {
  font-size: 18px;
  font-weight: 700;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.log-item {
  display: grid;
  grid-template-columns: 28px 64px 52px minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  padding: 4px 8px;
  font-size: 13px;
  border-radius: 4px;
  background: var(--bg-secondary);
}

.log-index {
  color: var(--text-secondary);
  font-size: 11px;
}

.log-buy { color: #ef4444; font-weight: 600; white-space: nowrap; }
.log-sell { color: #10b981; font-weight: 600; white-space: nowrap; }
.log-price { font-weight: 600; color: var(--text-primary); white-space: nowrap; }
.log-time {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-pnl-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -2px 0 6px 36px;
  padding: 4px 8px;
  font-size: 12px;
  border-left: 2px solid var(--border-color, rgba(128,128,128,0.2));
  color: var(--text-secondary);
}

.log-pnl-label {
  font-weight: 600;
}

.log-pnl-value {
  font-size: 14px;
  font-weight: 700;
}

/* 设置弹窗 */
.settings-modal-overlay {
  position: fixed;
  top: 0; left: 0;
  width: 100%; height: 100%;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.settings-modal {
  background: #fff;
  border-radius: 8px;
  width: 520px;
  max-width: 90vw;
  box-shadow: 0 10px 25px rgba(0,0,0,0.15);
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.settings-modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.settings-modal-header h3 {
  margin: 0;
  font-size: 16px;
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  line-height: 1;
}

.settings-modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.settings-group {
  margin-bottom: 20px;
}

.settings-group h4 {
  font-size: 14px;
  color: #666;
  margin: 0 0 10px 0;
  padding-bottom: 6px;
  border-bottom: 1px dashed #e0e0e0;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row label {
  font-size: 13px;
  color: #333;
}

.form-row input[type="number"] {
  width: 70px;
  padding: 4px 8px;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  margin-left: 4px;
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 10px 16px;
}

.color-grid label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #333;
}

.color-grid input[type="color"] {
  width: 36px;
  height: 26px;
  padding: 0;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.form-hint {
  font-size: 12px;
  color: #e67e22;
  margin: 6px 0 0;
}

.radio-label, .checkbox-label {
  display: block;
  font-size: 14px;
  color: #333;
  margin-bottom: 6px;
  cursor: pointer;
}

.radio-label input, .checkbox-label input {
  margin-right: 6px;
}

.settings-modal-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e0e0e0);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.vip-status-chip {
  min-width: 108px;
}

.vip-modal {
  width: 560px;
}

.vip-status-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  background: #fffbeb;
  color: #92400e;
}

.vip-status-summary.vip {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.vip-status-summary i {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(245, 158, 11, 0.16);
}

.vip-status-summary.vip i {
  background: rgba(16, 185, 129, 0.16);
}

.vip-status-summary div,
.vip-switch-row div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.vip-status-summary strong,
.vip-switch-row strong {
  font-size: 14px;
  color: inherit;
}

.vip-status-summary span,
.vip-switch-row span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.vip-copy {
  margin: 14px 0;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.7;
}

.vip-copy p {
  margin: 0 0 8px;
}

.vip-pay-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  margin-bottom: 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.vip-qr {
  width: 112px;
  height: 112px;
  flex: 0 0 auto;
  object-fit: contain;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: #fff;
}

.vip-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
}

.vip-switch {
  position: relative;
  width: 46px;
  height: 26px;
  flex: 0 0 auto;
  cursor: pointer;
}

.vip-switch input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.vip-switch span {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #d1d5db;
  transition: background 0.15s;
}

.vip-switch span::after {
  content: "";
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.18);
  transition: transform 0.15s;
}

.vip-switch input:checked + span {
  background: var(--success-color);
}

.vip-switch input:checked + span::after {
  transform: translateX(20px);
}

.vip-switch input:disabled + span {
  opacity: 0.65;
}

.vip-disclaimer {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f8fafc;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.record-modal {
  width: 680px;
}

.record-modal.small {
  width: 520px;
}

.record-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.record-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #333;
}

.record-form input,
.record-form select {
  width: 100%;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #d0d0d0;
  border-radius: 4px;
  background: #fff;
  color: #333;
}

.save-preview,
.record-detail-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.save-preview div,
.record-detail-stats div {
  padding: 10px 12px;
  border: 1px solid var(--border-color, rgba(128,128,128,0.15));
  border-radius: 6px;
  background: var(--bg-secondary, #f8fafc);
  min-width: 0;
}

.save-preview span,
.record-detail-stats span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.save-preview strong,
.record-detail-stats strong {
  display: block;
  font-size: 14px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  background: var(--bg-secondary, #f8fafc);
  border: 1px dashed var(--border-color, #d0d0d0);
  border-radius: 6px;
  font-size: 13px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.record-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-color, rgba(128,128,128,0.18));
  border-radius: 6px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.record-row:hover {
  border-color: var(--primary-color, #3b82f6);
  background: var(--bg-hover, #f4f7fb);
}

.record-main,
.record-side {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.record-main strong,
.record-side strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.record-main small,
.record-side small {
  color: var(--text-secondary);
  font-size: 12px;
}

.record-side {
  align-items: flex-end;
}

.record-detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.record-detail-head h4 {
  margin: 0;
  font-size: 15px;
  color: var(--text-primary);
}

.record-detail-head p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.record-trade-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.record-trade {
  display: grid;
  grid-template-columns: 32px 70px 60px minmax(0, 1fr) 76px;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 4px;
  background: var(--bg-secondary, #f8fafc);
  font-size: 13px;
}

.record-pnl {
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .record-form,
  .save-preview,
  .record-detail-stats {
    grid-template-columns: 1fr;
  }

  .record-row,
  .record-trade {
    grid-template-columns: 1fr;
  }

  .record-side {
    align-items: flex-start;
  }
}
</style>
