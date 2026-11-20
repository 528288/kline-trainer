# K 线训练器（KLine Trainer）

一个用**历史 K 线做「盲练」**的复盘训练工具：每次随机抽一只股票，只给你到某一根 K 线为止的历史，
剩下的逐根揭示，由你判断下一根涨跌、决定买入卖出，最后统计胜率和盈亏。

因为看不到右侧未来走势，练的是**当下的判断**，而不是对着已完成的图形事后诸葛亮。

本仓库包含两个平台版本，共用同一份行情数据：

| 版本 | 技术栈 | 入口 |
|---|---|---|
| [PC 版](pc/) | Python 3.10+ / 原生 HTTP 服务 / Vue 3 + KLineCharts | `pc/` |
| [Android 版](android/) | Kotlin / Jetpack Compose / Room / Hilt | `android/` |

---

## 立即开始（不想配环境）

| 方式 | 链接 | 说明 |
|---|---|---|
| 🌐 **在线版** | https://528288.github.io/kline-trainer/ | 浏览器打开即用，数据全在本地，无需安装 |
| 🖥 **PC 免安装包** | [Releases](https://github.com/528288/kline-trainer/releases/latest) → `KLineTrainer-PC-*.zip` | 解压双击 `KLineTrainer.exe`，已内置行情数据 |
| 📱 **Android APK** | [Releases](https://github.com/528288/kline-trainer/releases/latest) → `KLineTrainer-android-*.apk` | 直接安装，已内置行情数据（Android 8.0+） |

> Release 包都**内置了完整行情数据**，开箱即用。
> 想从源码跑或自己打包，见下方「快速开始」。

---

## 目录结构

```
kline-trainer/
├── android/                 # Android App（Kotlin + Compose）
│   ├── app/src/main/java/com/stock/trainer/
│   ├── app/src/main/assets/kline/   # 行情数据镜像（构建前由脚本同步，不入库）
│   └── scripts/sync_kline_assets.ps1
├── pc/                      # PC 版（Python 后端 + Vue 前端）
│   ├── src/kline_trainer/
│   ├── frontend/
│   └── scripts/build_exe.ps1
├── data/
│   └── kline/               # 行情数据：<代码>.zip，两个平台共用的唯一数据源
├── pages/                   # GitHub Pages 在线版（站点根）
├── docs/
│   ├── 数据格式.md
│   ├── 架构.md
│   └── 发布说明.md
└── LICENSE
```

**`data/kline/` 是唯一数据源。** PC 版直接读它；Android 版用
`android/scripts/sync_kline_assets.ps1` 镜像进 `assets/`，构建时打进 APK。

---

## 快速开始

### PC 版

```bash
cd pc
pip install -r requirements-build.txt   # 仅打包需要；直接运行只要 Python 3.10+
PYTHONPATH=src python -m kline_trainer
```

Windows PowerShell：

```powershell
cd pc
$env:PYTHONPATH = "src"
python -m kline_trainer
```

程序会启动本地 HTTP 服务（默认 `127.0.0.1:8765`，端口被占用时自动顺延）并自动打开浏览器。

常用参数：

```text
--host 127.0.0.1       监听地址
--port 8765            起始端口
--source <目录>        临时指定行情数据目录
--data-dir <目录>      配置/训练记录的存放目录（默认 %LOCALAPPDATA%\KLineTrainerStandalone）
--no-browser           不自动打开浏览器
```

### Android 版

```powershell
cd android
pwsh -File scripts/sync_kline_assets.ps1   # 把 data/kline 同步进 assets
.\gradlew.bat :app:assembleDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`

环境要求：JDK 17、Android SDK（`compileSdk 35`），并在 `android/local.properties` 里配好
`sdk.dir`（Android Studio 打开项目会自动生成）。

---

## 两个版本的差异

| | PC 版 | Android 版 |
|---|---|---|
| 运行形态 | 本地 HTTP 服务 + 浏览器 | 原生 App |
| 行情数据 | 直接读磁盘 `data/kline` | 打包进 APK，首次启动导入 Room |
| 图表 | [KLineCharts](https://github.com/klinecharts/KLineCharts)（Web Canvas） | 自绘 Canvas + Compose |
| 数据存储 | SQLite（训练记录）/ JSON（配置） | Room + SharedPreferences |
| 绘制工具 | 有（趋势线、水平线等，按股票+周期持久化） | 有（自绘 DrawingRenderer） |
| 适用场景 | 大屏复盘、批量练 | 通勤碎片时间练 |

两端共有的核心玩法：**盲练标的 / 单步与双步揭示 / 手动与自动播放 / 买入卖出平仓 /
多周期切换 / MA·VOL·MACD·KDJ·RSI·BOLL 指标 / 训练记录与胜率统计**。

---

## 玩法说明

1. **开一局**：系统从数据里随机抽一只股票，取最近 N 根日 K（默认 300 根），
   前 `displayCount` 根（默认 150）作为「已看到的历史」，后 `trainCount` 根（默认 150）作为训练段。
2. **逐根揭示**：
   - **单步（one_step）**：一次揭示一整根 K 线。
   - **双步（two_step）**：先只看开盘价，让你判断方向/下单，再揭晓收盘——
     这是更接近实盘的练法，也是默认模式。
3. **下单**：买入 / 卖出 / 平仓，记录成交价、数量、盈亏。
4. **结束**：保存训练记录，进入统计页看胜率、总盈亏、累计盈亏、每笔明细。

**关于多周期**：数据里日 K 可能有十几年，但 60 分钟线只覆盖最近的交易日。
如果直接在全量日 K 里随机切训练段，大部分起点会落在 60 分钟没有数据的区间，
切到分钟标签页就变成「另一段历史」。所以训练段会先**裁剪到 60 分钟线实际覆盖的区间**，
保证日 / 周 / 分钟三周期严格对齐（见 `PeriodAligner`）。

---

## 行情数据

`data/kline/<代码>.zip`，每个 zip 内含 `day.csv` / `week.csv` / `60min.csv`，
列格式 `date,open,high,low,close,volume`。详见 [docs/数据格式.md](docs/数据格式.md)。

仓库自带约 3000 只标的的示例数据。若要换成自己的数据，只要遵循同样的格式替换
`data/kline/` 即可，两端都会自动识别。也提供了从 QMT 导出的脚本：
`android/scripts/export_app_kline_from_qmt.py`。

---

## 文档

- [docs/架构.md](docs/架构.md) —— 两端模块划分、数据流、存储设计
- [docs/数据格式.md](docs/数据格式.md) —— zip / CSV 格式说明与校验要点
- [docs/发布说明.md](docs/发布说明.md) —— 签名信息、CI 发布流程

## 校验和

Release 附件 SHA-256（自行核对下载完整性）：

```text
KLineTrainer-PC-v1.0.0.zip
  4E1DF06B6F986E224402812DF0291C13B6F555FA1740B8FB65BAE59F5DB75105

KLineTrainer-android-v1.0.0.apk
  901909A843EE0CE8F8D7E3C3DCE53BF312B95779736CFFFBA2FC77479B1425CA
```

Windows 校验：`Get-FileHash <文件> -Algorithm SHA256`

---

## 免责声明

本项目仅用于**技术学习与个人复盘训练**，不构成任何投资建议。
仓库内的行情数据仅作功能演示，请勿用于实际交易决策。
据此操作，风险自负。

## 许可

[MIT](LICENSE)
