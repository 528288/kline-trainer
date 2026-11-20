# KLine Trainer Standalone

独立 K 线训练器。默认数据源放在程序同级的 `data/kline` 目录。

开发运行时默认读取：

```text
data/kline/
```

打包发布后默认读取：

```text
dist/KLineTrainer/data/kline/
```

发布目录结构应类似：

```text
dist/KLineTrainer/
  KLineTrainer.exe
  data/
    kline/
      000008.zip
      000010.zip
      ...
```

每个 zip 内部使用以下 CSV 文件：

```text
day.csv
week.csv
month.csv
60min.csv
```

CSV 表头：

```text
date,open,high,low,close,volume
```

## 开发运行

```powershell
cd y:\gitstore\kline_trainer_standalone
python -m kline_trainer
```

如果没有设置 `PYTHONPATH`，也可以这样运行：

```powershell
cd y:\gitstore\kline_trainer_standalone
$env:PYTHONPATH = "src"
python -m kline_trainer
```

也可以临时指定外部数据源：

```powershell
python -m kline_trainer --source "D:\your-data-root"
```

## 打包

```powershell
cd y:\gitstore\kline_trainer_standalone
.\scripts\build_exe.ps1
```

构建脚本会把项目内的 `data` 目录复制到 `dist/KLineTrainer/data`。后续更新行情数据时，替换 exe 同级的 `data/kline` 即可，不需要重新打包程序。

打包完成后运行：

```powershell
.\dist\KLineTrainer\KLineTrainer.exe
```

程序会启动本机 HTTP 服务并自动打开浏览器。训练记录和配置保存在：

```text
%LOCALAPPDATA%\KLineTrainerStandalone
```
