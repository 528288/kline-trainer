import csv
import io
import random
import zipfile
from dataclasses import dataclass
from pathlib import Path


PERIOD_FILES = {
    "1d": "day.csv",
    "day": "day.csv",
    "week": "week.csv",
    "month": "month.csv",
    "60m": "60min.csv",
    "60min": "60min.csv",
}


@dataclass(frozen=True)
class DataStatus:
    source_path: str
    kline_dir: str
    exists: bool
    zip_count: int
    message: str = ""


class KLineDataSource:
    def __init__(self, source_path: str | Path):
        self.source_path = Path(source_path)
        self.kline_dir = self._resolve_kline_dir(self.source_path)
        self._day_count_cache: dict[str, tuple[int, int, int]] = {}

    def status(self) -> DataStatus:
        exists = self.kline_dir.exists() and self.kline_dir.is_dir()
        count = len(list(self.kline_dir.glob("*.zip"))) if exists else 0
        if not exists:
            msg = f"未找到 K 线数据目录: {self.kline_dir}"
        elif count == 0:
            msg = "kline 数据目录下没有 zip 文件"
        else:
            msg = ""
        return DataStatus(
            source_path=str(self.source_path),
            kline_dir=str(self.kline_dir),
            exists=exists,
            zip_count=count,
            message=msg,
        )

    def list_symbols(self) -> list[str]:
        if not self.kline_dir.exists():
            return []
        return sorted(path.stem for path in self.kline_dir.glob("*.zip"))

    def trainable_symbols(self, min_day_bars: int) -> list[str]:
        return [
            symbol
            for symbol in self.list_symbols()
            if self.day_count(symbol) >= min_day_bars
        ]

    def build_random_session(self, config: dict) -> dict:
        total_bars = int(_config_value(config, "total_bars", "totalBars"))
        display_count = int(_config_value(config, "display_count", "displayCount"))
        train_count = int(_config_value(config, "train_count", "trainCount"))

        symbols = self.list_symbols()
        if not symbols:
            status = self.status()
            detail = status.message or "没有可用股票数据"
            raise ValueError(detail)

        random.shuffle(symbols)
        last_error = None
        checked = 0
        for symbol in symbols:
            if self.day_count(symbol) < total_bars:
                continue
            checked += 1
            try:
                return self.build_session(symbol, total_bars, display_count, train_count)
            except Exception as exc:
                last_error = exc
            if checked >= 300:
                break
        raise ValueError(f"无法创建训练会话: {last_error}")

    def build_session(
        self,
        symbol: str,
        total_bars: int,
        display_count: int,
        train_count: int,
    ) -> dict:
        day_bars = self.read_bars(symbol, "1d")
        if len(day_bars) < total_bars:
            raise ValueError(f"{symbol} 日线数据不足，需 {total_bars} 根，实有 {len(day_bars)} 根")

        day_window = day_bars[-total_bars:]
        daily_display = day_window[:display_count]
        daily_train = day_window[display_count : display_count + train_count]
        if not daily_train:
            raise ValueError(f"{symbol} 数据不足，无法开始训练")

        min_date = _date_key(day_window[0]["date"])
        max_date = _date_key(day_window[-1]["date"])
        split_date = _date_key(daily_display[-1]["date"]) if daily_display else min_date

        bars = {
            "1d": {
                "display": daily_display,
                "train": daily_train,
            }
        }

        for frontend_period, period_bars in self._build_periods(symbol, day_window).items():
            ranged = [item for item in period_bars if min_date <= _date_key(item["date"]) <= max_date]
            split = _split_by_date(ranged, split_date)
            if split["display"] or split["train"]:
                bars[frontend_period] = {
                    "display": split["display"][-total_bars * 5 :],
                    "train": split["train"][-total_bars * 10 :],
                }

        return {
            "success": True,
            "stock_code": symbol,
            "stock_name": symbol,
            "data_source": str(self.kline_dir),
            "total_bars": total_bars,
            "display_count": len(daily_display),
            "train_count": len(daily_train),
            "bars": bars,
        }

    def read_bars(self, symbol: str, period: str) -> list[dict]:
        member = PERIOD_FILES.get(period)
        if not member:
            raise ValueError(f"不支持的周期: {period}")
        zip_path = self._zip_path(symbol)
        if not zip_path.exists():
            raise FileNotFoundError(f"未找到 {symbol}.zip")

        with zipfile.ZipFile(zip_path) as archive:
            if member not in archive.namelist():
                return []
            with archive.open(member) as raw:
                text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
                return sorted(
                    (bar for bar in _parse_csv(text) if _is_valid_bar(bar)),
                    key=lambda item: item["date"],
                )

    def day_count(self, symbol: str) -> int:
        zip_path = self._zip_path(symbol)
        if not zip_path.exists():
            return 0
        stat = zip_path.stat()
        cache_key = str(zip_path)
        cached = self._day_count_cache.get(cache_key)
        marker = (stat.st_mtime_ns, stat.st_size)
        if cached and cached[:2] == marker:
            return cached[2]

        try:
            with zipfile.ZipFile(zip_path) as archive:
                if "day.csv" not in archive.namelist():
                    count = 0
                else:
                    with archive.open("day.csv") as raw:
                        text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
                        first_line = text.readline()
                        if not first_line:
                            count = 0
                        else:
                            first_count = 0 if _looks_like_header(first_line) else 1
                            count = first_count + sum(1 for line in text if line.strip())
        except Exception:
            count = 0
        self._day_count_cache[cache_key] = (*marker, count)
        return count

    def _zip_path(self, symbol: str) -> Path:
        safe_symbol = "".join(ch for ch in str(symbol) if ch.isalnum())
        return self.kline_dir / f"{safe_symbol}.zip"

    def _build_periods(self, symbol: str, day_window: list[dict]) -> dict[str, list[dict]]:
        periods: dict[str, list[dict]] = {}
        for frontend_period, source_period in (("week", "week"), ("month", "month"), ("60m", "60m")):
            try:
                bars = self.read_bars(symbol, source_period)
            except Exception:
                bars = []
            if bars:
                periods[frontend_period] = bars

        periods.setdefault("week", _aggregate_calendar(day_window, "iso_week"))
        periods.setdefault("month", _aggregate_calendar(day_window, "month"))
        periods["year"] = _aggregate_calendar(day_window, "year")
        periods["3d"] = _aggregate_by_count(day_window, 3)
        periods["5d"] = _aggregate_by_count(day_window, 5)
        if periods.get("60m"):
            periods["120m"] = _aggregate_intraday(periods["60m"], 2)
        return periods

    @staticmethod
    def _resolve_kline_dir(source_path: Path) -> Path:
        candidates = [
            source_path,
            source_path / "kline",
            source_path / "assets" / "kline",
            source_path / "data" / "kline",
            source_path / "src" / "main" / "assets" / "kline",
        ]
        for candidate in candidates:
            if candidate.exists() and candidate.is_dir() and any(candidate.glob("*.zip")):
                return candidate
        if source_path.name.lower() == "data":
            return source_path / "kline"
        return source_path / "src" / "main" / "assets" / "kline"


def _parse_csv(text) -> list[dict]:
    first_line = text.readline()
    if not first_line:
        return []

    rows = []
    if _looks_like_header(first_line):
        headers = next(csv.reader([first_line]))
        reader = csv.DictReader(text, fieldnames=headers)
        for row in reader:
            rows.append(_bar_from_mapping(row))
    else:
        reader = csv.reader([first_line] + list(text))
        for row in reader:
            rows.append(_bar_from_sequence(row))
    return [row for row in rows if row is not None]


def _bar_from_mapping(row: dict) -> dict | None:
    lower = {str(key).strip().lower(): value for key, value in row.items()}
    return _make_bar(
        lower.get("date") or lower.get("time") or lower.get("datetime"),
        lower.get("open"),
        lower.get("high"),
        lower.get("low"),
        lower.get("close"),
        lower.get("volume"),
    )


def _bar_from_sequence(row: list[str]) -> dict | None:
    if len(row) < 5:
        return None
    return _make_bar(
        row[0],
        row[1],
        row[2],
        row[3],
        row[4],
        row[5] if len(row) > 5 else 0,
    )


def _make_bar(date, open_, high, low, close, volume) -> dict | None:
    try:
        date_value = str(date or "").strip()
        if not date_value:
            return None
        return {
            "date": date_value,
            "open": float(open_),
            "high": float(high),
            "low": float(low),
            "close": float(close),
            "volume": int(float(volume or 0)),
        }
    except (TypeError, ValueError):
        return None


def _looks_like_header(line: str) -> bool:
    lower = line.lower()
    return "date" in lower or "time" in lower or "open" in lower


def _is_valid_bar(bar: dict | None) -> bool:
    if not bar:
        return False
    values = [bar["open"], bar["high"], bar["low"], bar["close"]]
    return all(value == value and value > 0 for value in values) and bar["high"] >= bar["low"]


def _date_key(value: str) -> str:
    return str(value)[:10]


def _config_value(config: dict, snake: str, camel: str):
    if snake in config:
        return config[snake]
    return config[camel]


def _split_by_date(bars: list[dict], split_date: str) -> dict[str, list[dict]]:
    return {
        "display": [item for item in bars if _date_key(item["date"]) <= split_date],
        "train": [item for item in bars if _date_key(item["date"]) > split_date],
    }


def _aggregate_by_count(bars: list[dict], count: int) -> list[dict]:
    result = []
    for index in range(0, len(bars), count):
        group = bars[index : index + count]
        if len(group) == count:
            result.append(_aggregate_group(group))
    return result


def _aggregate_calendar(bars: list[dict], kind: str) -> list[dict]:
    groups: list[list[dict]] = []
    current_key = None
    current_group: list[dict] = []
    for bar in bars:
        key = _calendar_key(bar["date"], kind)
        if current_group and key != current_key:
            groups.append(current_group)
            current_group = []
        current_key = key
        current_group.append(bar)
    if current_group:
        groups.append(current_group)
    return [_aggregate_group(group) for group in groups if group]


def _aggregate_intraday(bars: list[dict], count: int) -> list[dict]:
    result = []
    grouped: dict[str, list[dict]] = {}
    for bar in bars:
        grouped.setdefault(_date_key(bar["date"]), []).append(bar)
    for date in sorted(grouped):
        day_bars = sorted(grouped[date], key=lambda item: item["date"])
        result.extend(_aggregate_by_count(day_bars, count))
    return result


def _aggregate_group(group: list[dict]) -> dict:
    return {
        "date": group[-1]["date"],
        "open": group[0]["open"],
        "high": max(item["high"] for item in group),
        "low": min(item["low"] for item in group),
        "close": group[-1]["close"],
        "volume": sum(int(item.get("volume") or 0) for item in group),
    }


def _calendar_key(date: str, kind: str):
    from datetime import datetime

    value = datetime.strptime(_date_key(date), "%Y-%m-%d").date()
    if kind == "iso_week":
        year, week, _ = value.isocalendar()
        return year, week
    if kind == "year":
        return value.year
    return value.year, value.month
