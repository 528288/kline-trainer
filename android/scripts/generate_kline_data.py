#!/usr/bin/env python3
"""生成模拟K线数据，打包为ZIP放入assets/kline/"""

import csv
import os
import random
import zipfile
from datetime import datetime, timedelta
from pathlib import Path

random.seed(42)

# 模拟品种列表
SYMBOLS = [
    ("600519", "贵州茅台"),
    ("000858", "五粮液"),
    ("300750", "宁德时代"),
    ("000333", "美的集团"),
    ("601318", "中国平安"),
]

OUTPUT_DIR = Path("app/src/main/assets/kline")


def generate_daily_prices(days: int, start_price: float, volatility: float):
    """基于随机游走生成日线数据"""
    prices = []
    price = start_price
    date = datetime(2019, 1, 4)  # 从2019年开始

    for i in range(days):
        # 跳过周末
        while date.weekday() >= 5:
            date += timedelta(days=1)

        # 随机涨跌（带轻微向上的漂移）
        change_pct = random.gauss(0.0003, volatility)
        open_price = price
        close_price = open_price * (1 + change_pct)

        # 日内波动
        intraday_range = abs(close_price - open_price) + price * random.uniform(0.005, 0.03)
        high = max(open_price, close_price) + intraday_range * random.uniform(0.1, 0.5)
        low = min(open_price, close_price) - intraday_range * random.uniform(0.1, 0.5)
        low = max(low, 0.01)

        volume = int(price * random.uniform(10000, 500000))

        prices.append({
            "date": date.strftime("%Y-%m-%d"),
            "open": round(open_price, 2),
            "high": round(high, 2),
            "low": round(low, 2),
            "close": round(close_price, 2),
            "volume": volume,
        })

        price = close_price
        date += timedelta(days=1)

    return prices


def daily_to_weekly(daily_data):
    """日线聚合为周线"""
    weeks = {}
    for row in daily_data:
        d = datetime.strptime(row["date"], "%Y-%m-%d")
        # ISO week
        week_key = d.strftime("%Y-W%W")
        if week_key not in weeks:
            weeks[week_key] = []
        weeks[week_key].append(row)

    result = []
    for week_key in sorted(weeks.keys()):
        rows = weeks[week_key]
        if len(rows) < 2:
            continue
        result.append({
            "date": rows[-1]["date"],
            "open": rows[0]["open"],
            "high": max(r["high"] for r in rows),
            "low": min(r["low"] for r in rows),
            "close": rows[-1]["close"],
            "volume": sum(r["volume"] for r in rows),
        })
    return result


def daily_to_monthly(daily_data):
    """日线聚合为月线"""
    months = {}
    for row in daily_data:
        month_key = row["date"][:7]
        if month_key not in months:
            months[month_key] = []
        months[month_key].append(row)

    result = []
    for month_key in sorted(months.keys()):
        rows = months[month_key]
        if len(rows) < 5:
            continue
        result.append({
            "date": rows[-1]["date"],
            "open": rows[0]["open"],
            "high": max(r["high"] for r in rows),
            "low": min(r["low"] for r in rows),
            "close": rows[-1]["close"],
            "volume": sum(r["volume"] for r in rows),
        })
    return result


def daily_to_60min(daily_data):
    """从日线数据生成60分钟线（模拟日内4根K线）"""
    result = []
    for row in daily_data:
        d = row["date"]
        base_open = row["open"]
        base_close = row["close"]
        base_high = row["high"]
        base_low = row["low"]
        daily_range = base_high - base_low

        # 4根60分钟K线（A股每天4小时交易）
        times = ["09:00", "10:00", "11:00", "12:00"]
        prev_close = base_open

        for i, t in enumerate(times):
            progress = (i + 1) / 4
            target = base_open + (base_close - base_open) * progress
            noise = daily_range * random.uniform(-0.3, 0.3)
            close_60 = target + noise

            # 确保在日内高低点范围内
            close_60 = max(base_low, min(base_high, close_60))

            o = prev_close
            c = close_60
            h = max(o, c) + abs(daily_range) * random.uniform(0.0, 0.3)
            l = min(o, c) - abs(daily_range) * random.uniform(0.0, 0.3)
            h = max(min(h, base_high), max(o, c))
            l = min(max(l, base_low), min(o, c))

            result.append({
                "date": f"{d} {t}",
                "open": round(o, 2),
                "high": round(h, 2),
                "low": round(l, 2),
                "close": round(c, 2),
                "volume": int(row["volume"] * random.uniform(0.15, 0.35)),
            })
            prev_close = close_60

    return result


def write_csv(filepath, rows):
    with open(filepath, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["date", "open", "high", "low", "close", "volume"])
        w.writeheader()
        w.writerows(rows)


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    base_prices = {
        "600519": 590.0,
        "000858": 50.0,
        "300750": 30.0,
        "000333": 40.0,
        "601318": 60.0,
    }
    volatilities = {
        "600519": 0.018,
        "000858": 0.022,
        "300750": 0.028,
        "000333": 0.016,
        "601318": 0.014,
    }

    for symbol, name in SYMBOLS:
        print(f"Generating data for {symbol} {name}...")

        days = 1700  # ~7 years of trading days
        daily = generate_daily_prices(days, base_prices[symbol], volatilities[symbol])
        weekly = daily_to_weekly(daily)
        monthly = daily_to_monthly(daily)
        min60 = daily_to_60min(daily)

        # Write temporary CSV files
        tmp_dir = Path(f"/tmp/kline_{symbol}")
        tmp_dir.mkdir(exist_ok=True)

        files = {
            "month.csv": monthly,
            "week.csv": weekly,
            "day.csv": daily,
            "60min.csv": min60,
        }

        for filename, data in files.items():
            write_csv(tmp_dir / filename, data)
            print(f"  {filename}: {len(data)} rows")

        # Create ZIP
        zip_path = OUTPUT_DIR / f"{symbol}.zip"
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for filename in files:
                zf.write(tmp_dir / filename, filename)

        print(f"  -> {zip_path} ({zip_path.stat().st_size / 1024:.1f} KB)")
        print()

    print("Done! All ZIP files generated.")


if __name__ == "__main__":
    main()
