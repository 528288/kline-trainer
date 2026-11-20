#!/usr/bin/env python3
"""Export QMT k-line data to the APK initializer format.

This script is intentionally placed under scripts/ so it is not packaged into
the APK. The generated ZIP files can be reviewed and then copied to
app/src/main/assets/kline when needed.

APK format:
  kline/{symbol}.zip
    day.csv
    week.csv
    60min.csv

CSV columns:
  date,open,high,low,close,volume

Date formats accepted by the app:
  day/week: yyyy-MM-dd
  60min:    yyyy-MM-dd HH:mm
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import shutil
import sys
import zipfile
from dataclasses import asdict, dataclass, field
from datetime import date
from pathlib import Path
from typing import Iterable

import pandas as pd


DEFAULT_QMT_ROOT = Path("Y:/gitstore/stock_selector_f")
DEFAULT_OUTPUT_DIR = Path("build/kline_export")
REQUIRED_APP_PERIODS = ("day", "week", "60min")
DEFAULT_PERIODS = ("day", "week", "60min")
QMT_FETCH_PERIODS = {
    "day": "1d",
    "60min": "60min",
}


@dataclass
class SymbolReport:
    symbol: str
    qualified: bool = False
    included: bool = False
    reasons: list[str] = field(default_factory=list)
    counts: dict[str, int] = field(default_factory=dict)
    start_dates: dict[str, str] = field(default_factory=dict)
    end_dates: dict[str, str] = field(default_factory=dict)
    sixty_min_day_coverage: float = 0.0
    zip_bytes: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export QMT data as per-symbol ZIP files for the Android app."
    )
    parser.add_argument("--qmt-root", type=Path, default=DEFAULT_QMT_ROOT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--start", default="20130101", help="Start date, YYYYMMDD.")
    parser.add_argument("--end", default="today", help="End date, YYYYMMDD or today.")
    parser.add_argument("--adjust", default="qfq", choices=["qfq", "hfq", "none", "front", "back"])
    parser.add_argument("--codes", default="", help="Comma separated symbols, e.g. 000001,600000.")
    parser.add_argument("--codes-file", type=Path, default=None, help="TXT/CSV file with symbols.")
    parser.add_argument(
        "--exclude-boards",
        nargs="*",
        default=[],
        choices=["gem", "star", "bj", "etf"],
        help="Optional board filters. etf uses common ETF code prefixes.",
    )
    parser.add_argument(
        "--periods",
        default=",".join(DEFAULT_PERIODS),
        help="App periods to export. Default: day,week,60min.",
    )
    parser.add_argument("--min-day-bars", type=int, default=300)
    parser.add_argument("--min-60min-day-coverage", type=float, default=0.85)
    parser.add_argument(
        "--align-to-60min",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Trim day/week data to the date range covered by 60min before validation.",
    )
    parser.add_argument(
        "--max-total-mb",
        type=float,
        default=300.0,
        help="Max total generated ZIP size. Default keeps the APK data payload under 300 MB.",
    )
    parser.add_argument(
        "--target-symbols",
        type=int,
        default=0,
        help="Stop when total included ZIP files in output reaches this number. 0 means no target count.",
    )
    parser.add_argument("--max-symbols", type=int, default=0, help="0 means no explicit symbol count limit.")
    parser.add_argument("--price-decimals", type=int, default=6)
    parser.add_argument("--compresslevel", type=int, default=9)
    parser.add_argument("--clean", action="store_true", help="Remove output dir before exporting.")
    parser.add_argument(
        "--resume-existing",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="When output already has ZIP files, validate/count them and skip re-exporting those symbols.",
    )
    parser.add_argument(
        "--continue-after-budget",
        action="store_true",
        help="Keep scanning symbols after the size budget is reached.",
    )
    return parser.parse_args()


def import_qmt_fetcher(qmt_root: Path):
    qmt_root = qmt_root.resolve()
    if not qmt_root.exists():
        raise FileNotFoundError(f"QMT project root not found: {qmt_root}")
    sys.path.insert(0, str(qmt_root))
    from data.fetcher.qmt_fetcher import QMTFetcher  # type: ignore

    return QMTFetcher


def normalize_symbol(raw: str) -> str | None:
    token = str(raw).strip().upper()
    if not token or token == "NAN":
        return None
    if "." in token:
        token = token.split(".", 1)[0]
    if token.startswith(("SH", "SZ", "BJ")) and len(token) >= 8:
        token = token[2:]
    digits = "".join(ch for ch in token if ch.isdigit())
    if not digits:
        return None
    return digits.zfill(6)[-6:]


def load_codes(args: argparse.Namespace, fetcher) -> list[str]:
    raw_codes: list[str] = []
    if args.codes:
        raw_codes.extend(part.strip() for part in args.codes.split(","))
    elif args.codes_file:
        raw_codes.extend(read_codes_file(args.codes_file))
    else:
        raw_codes.extend(fetcher.get_all_codes())

    excluded = set(args.exclude_boards or [])
    result: list[str] = []
    seen: set[str] = set()
    for raw in raw_codes:
        symbol = normalize_symbol(raw)
        if not symbol or is_excluded(symbol, excluded):
            continue
        if symbol not in seen:
            seen.add(symbol)
            result.append(symbol)
    return result


def read_codes_file(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(path)
    if path.suffix.lower() == ".csv":
        df = pd.read_csv(path, dtype=str)
        for col in ("symbol", "ts_code", "code"):
            if col in df.columns:
                return df[col].dropna().astype(str).tolist()
        return df.iloc[:, 0].dropna().astype(str).tolist()
    return [line.strip().split(",")[0] for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def is_excluded(symbol: str, excluded: set[str]) -> bool:
    if "gem" in excluded and symbol.startswith(("300", "301")):
        return True
    if "star" in excluded and symbol.startswith("688"):
        return True
    if "bj" in excluded and symbol.startswith(("4", "8")):
        return True
    if "etf" in excluded and symbol.startswith(("15", "16", "51", "56", "58")):
        return True
    return False


def today_yyyymmdd() -> str:
    return date.today().strftime("%Y%m%d")


def fetch_qmt(fetcher, symbol: str, app_period: str, start: str, end: str, adjust: str) -> pd.DataFrame:
    qmt_period = QMT_FETCH_PERIODS[app_period]
    df = fetcher.get_history(symbol, qmt_period, start, end, adjust=adjust)
    return normalize_ohlcv(df, app_period)


def normalize_ohlcv(df: pd.DataFrame | None, period: str) -> pd.DataFrame:
    if df is None or df.empty:
        return empty_ohlcv()

    out = df.copy()
    out.columns = [str(c).lower() for c in out.columns]
    if "date" not in out.columns:
        out = out.reset_index()
        out.columns = [str(c).lower() for c in out.columns]
        if "index" in out.columns and "date" not in out.columns:
            out = out.rename(columns={"index": "date"})

    rename_map = {"time": "date", "trade_date": "date", "vol": "volume"}
    out = out.rename(columns={k: v for k, v in rename_map.items() if k in out.columns})

    required = ["date", "open", "high", "low", "close", "volume"]
    missing = [col for col in required if col not in out.columns]
    if missing:
        return empty_ohlcv()

    out = out[required].copy()
    out["date"] = parse_datetime_series(out["date"])
    for col in ["open", "high", "low", "close", "volume"]:
        out[col] = pd.to_numeric(out[col], errors="coerce")

    out = out.dropna(subset=["date", "open", "high", "low", "close"])
    if out.empty:
        return empty_ohlcv()

    valid = (
        out["open"].map(math.isfinite)
        & out["high"].map(math.isfinite)
        & out["low"].map(math.isfinite)
        & out["close"].map(math.isfinite)
        & (out["open"] > 0)
        & (out["high"] > 0)
        & (out["low"] > 0)
        & (out["close"] > 0)
        & (out["high"] >= out[["open", "close", "low"]].max(axis=1))
        & (out["low"] <= out[["open", "close", "high"]].min(axis=1))
        & (out["date"] <= pd.Timestamp.now() + pd.Timedelta(days=1))
    )
    out = out[valid].copy()
    if out.empty:
        return empty_ohlcv()

    out["volume"] = out["volume"].fillna(0).clip(lower=0).round().astype("int64")
    if period in {"day", "week"}:
        out["date"] = out["date"].dt.normalize()

    out = out.drop_duplicates(subset=["date"], keep="last")
    return out.sort_values("date").reset_index(drop=True)


def empty_ohlcv() -> pd.DataFrame:
    return pd.DataFrame(columns=["date", "open", "high", "low", "close", "volume"])


def parse_datetime_series(values: pd.Series) -> pd.Series:
    if pd.api.types.is_numeric_dtype(values):
        max_value = pd.to_numeric(values, errors="coerce").max()
        if pd.notna(max_value) and max_value > 10_000_000_000:
            return pd.to_datetime(values, unit="ms", errors="coerce")
    return pd.to_datetime(values.astype(str), errors="coerce")


def aggregate_period(day_df: pd.DataFrame, period: str) -> pd.DataFrame:
    if day_df.empty:
        return empty_ohlcv()
    df = day_df.copy()
    if period == "week":
        group_key = df["date"].dt.to_period("W-FRI")
    else:
        raise ValueError(period)

    rows = []
    for _, group in df.groupby(group_key, sort=True):
        group = group.sort_values("date")
        rows.append(
            {
                "date": group["date"].iloc[-1],
                "open": group["open"].iloc[0],
                "high": group["high"].max(),
                "low": group["low"].min(),
                "close": group["close"].iloc[-1],
                "volume": int(group["volume"].sum()),
            }
        )
    return pd.DataFrame(rows, columns=["date", "open", "high", "low", "close", "volume"])


def align_day_to_intraday(day_df: pd.DataFrame, min60_df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Keep the common date range covered by both daily and intraday data."""
    day_dates = set(day_df["date"].dt.date)
    min60_dates = set(min60_df["date"].dt.date)
    common_dates = sorted(day_dates & min60_dates)
    if not common_dates:
        return day_df.iloc[0:0].copy(), min60_df.iloc[0:0].copy()

    first_common = common_dates[0]
    last_common = common_dates[-1]
    trimmed_day = day_df[
        (day_df["date"].dt.date >= first_common)
        & (day_df["date"].dt.date <= last_common)
        & (day_df["date"].dt.date.isin(common_dates))
    ].copy()
    trimmed_min60 = min60_df[
        (min60_df["date"].dt.date >= first_common)
        & (min60_df["date"].dt.date <= last_common)
        & (min60_df["date"].dt.date.isin(common_dates))
    ].copy()
    return trimmed_day.reset_index(drop=True), trimmed_min60.reset_index(drop=True)


def validate_periods(period_data: dict[str, pd.DataFrame], min_day_bars: int, min_60_cov: float) -> tuple[bool, list[str], float]:
    reasons: list[str] = []
    day_df = period_data.get("day", empty_ohlcv())
    week_df = period_data.get("week", empty_ohlcv())
    min60_df = period_data.get("60min", empty_ohlcv())

    if len(day_df) < min_day_bars:
        reasons.append(f"day_bars_lt_{min_day_bars}")
    if week_df.empty:
        reasons.append("week_empty")
    elif len(week_df) < max(1, len(day_df) // 7):
        reasons.append("week_count_too_low")
    if min60_df.empty:
        reasons.append("60min_empty")

    coverage = 0.0
    if not day_df.empty and not min60_df.empty:
        day_dates = set(day_df["date"].dt.date)
        min60_dates = set(min60_df["date"].dt.date)
        matched = day_dates & min60_dates
        coverage = len(matched) / max(1, len(min60_dates))
        extra_dates = min60_dates - day_dates
        if coverage < min_60_cov:
            reasons.append(f"60min_day_coverage_lt_{min_60_cov:.2f}")
        if len(extra_dates) > max(5, int(len(min60_dates) * 0.02)):
            reasons.append("60min_has_many_dates_not_in_day")

    return not reasons, reasons, coverage


def period_dates(df: pd.DataFrame) -> tuple[str, str]:
    if df.empty:
        return "", ""
    return df["date"].min().strftime("%Y-%m-%d"), df["date"].max().strftime("%Y-%m-%d")


def fmt_float(value: float, decimals: int) -> str:
    number = float(value)
    text = f"{number:.{decimals}f}"
    if number > 0 and float(text) == 0:
        text = f"{number:.{decimals}g}"
    return text.rstrip("0").rstrip(".") if "." in text else text


def write_csv(path: Path, df: pd.DataFrame, period: str, decimals: int) -> None:
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp, lineterminator="\n")
        writer.writerow(["date", "open", "high", "low", "close", "volume"])
        for row in df.itertuples(index=False):
            dt_value = row.date
            date_text = dt_value.strftime("%Y-%m-%d %H:%M") if period == "60min" else dt_value.strftime("%Y-%m-%d")
            writer.writerow(
                [
                    date_text,
                    fmt_float(row.open, decimals),
                    fmt_float(row.high, decimals),
                    fmt_float(row.low, decimals),
                    fmt_float(row.close, decimals),
                    int(row.volume),
                ]
            )


def write_zip(staging_dir: Path, zip_path: Path, period_data: dict[str, pd.DataFrame], periods: Iterable[str], decimals: int, compresslevel: int) -> int:
    if staging_dir.exists():
        shutil.rmtree(staging_dir)
    staging_dir.mkdir(parents=True)

    csv_paths: list[tuple[Path, str]] = []
    for period in periods:
        df = period_data.get(period, empty_ohlcv())
        if df.empty:
            continue
        filename = f"{period}.csv"
        csv_path = staging_dir / filename
        write_csv(csv_path, df, period, decimals)
        csv_paths.append((csv_path, filename))

    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=compresslevel) as zf:
        for csv_path, arcname in csv_paths:
            zf.write(csv_path, arcname)
    shutil.rmtree(staging_dir)
    return zip_path.stat().st_size


def read_zip_period(zip_path: Path, period: str) -> pd.DataFrame:
    with zipfile.ZipFile(zip_path) as zf:
        filename = f"{period}.csv"
        if filename not in zf.namelist():
            return empty_ohlcv()
        with zf.open(filename) as fp:
            df = pd.read_csv(fp)
    return normalize_ohlcv(df, period)


def inspect_existing_zip(zip_path: Path, periods: tuple[str, ...], args: argparse.Namespace) -> SymbolReport:
    report = SymbolReport(symbol=zip_path.stem, zip_bytes=zip_path.stat().st_size)
    period_data: dict[str, pd.DataFrame] = {}

    try:
        for period in periods:
            period_data[period] = read_zip_period(zip_path, period)
    except Exception as exc:
        report.reasons.append(f"existing_zip_error:{type(exc).__name__}:{exc}")
        return report

    for period, df in period_data.items():
        report.counts[period] = len(df)
        start_date, end_date = period_dates(df)
        report.start_dates[period] = start_date
        report.end_dates[period] = end_date

    qualified, reasons, coverage = validate_periods(period_data, args.min_day_bars, args.min_60min_day_coverage)
    report.qualified = qualified
    report.included = qualified
    report.reasons.extend(reasons)
    report.sixty_min_day_coverage = coverage
    return report


def load_existing_reports(final_dir: Path, periods: tuple[str, ...], args: argparse.Namespace) -> tuple[list[SymbolReport], int, set[str]]:
    reports: list[SymbolReport] = []
    total_bytes = 0
    included_symbols: set[str] = set()

    for zip_path in sorted(final_dir.glob("*.zip")):
        report = inspect_existing_zip(zip_path, periods, args)
        reports.append(report)
        if report.included:
            total_bytes += report.zip_bytes
            included_symbols.add(report.symbol)

    return reports, total_bytes, included_symbols


def process_symbol(fetcher, symbol: str, args: argparse.Namespace, periods: tuple[str, ...], temp_dir: Path, final_dir: Path, current_total: int) -> tuple[SymbolReport, int, bool]:
    report = SymbolReport(symbol=symbol)
    period_data: dict[str, pd.DataFrame] = {}
    end = today_yyyymmdd() if str(args.end).lower() == "today" else args.end

    try:
        day_df = fetch_qmt(fetcher, symbol, "day", args.start, end, args.adjust)
        min60_df = fetch_qmt(fetcher, symbol, "60min", args.start, end, args.adjust) if "60min" in periods else empty_ohlcv()
        if args.align_to_60min and not day_df.empty and not min60_df.empty:
            day_df, min60_df = align_day_to_intraday(day_df, min60_df)
        period_data["day"] = day_df
        if "week" in periods:
            period_data["week"] = aggregate_period(day_df, "week")
        if "60min" in periods:
            period_data["60min"] = min60_df
    except Exception as exc:
        report.reasons.append(f"fetch_error:{type(exc).__name__}:{exc}")
        return report, current_total, False

    for period, df in period_data.items():
        report.counts[period] = len(df)
        start_date, end_date = period_dates(df)
        report.start_dates[period] = start_date
        report.end_dates[period] = end_date

    qualified, reasons, coverage = validate_periods(period_data, args.min_day_bars, args.min_60min_day_coverage)
    report.qualified = qualified
    report.reasons.extend(reasons)
    report.sixty_min_day_coverage = coverage
    if not qualified:
        return report, current_total, False

    temp_zip = temp_dir / f"{symbol}.zip"
    temp_zip.parent.mkdir(parents=True, exist_ok=True)
    zip_bytes = write_zip(
        staging_dir=temp_dir / f"{symbol}_csv",
        zip_path=temp_zip,
        period_data=period_data,
        periods=periods,
        decimals=args.price_decimals,
        compresslevel=args.compresslevel,
    )
    report.zip_bytes = zip_bytes

    budget_bytes = int(args.max_total_mb * 1024 * 1024)
    if current_total + zip_bytes > budget_bytes:
        report.reasons.append("budget_exceeded")
        temp_zip.unlink(missing_ok=True)
        return report, current_total, True

    final_zip = final_dir / f"{symbol}.zip"
    final_dir.mkdir(parents=True, exist_ok=True)
    final_zip.unlink(missing_ok=True)
    shutil.move(str(temp_zip), str(final_zip))
    report.included = True
    return report, current_total + zip_bytes, False


def write_reports(output_dir: Path, reports: list[SymbolReport], total_bytes: int, periods: tuple[str, ...], args: argparse.Namespace) -> None:
    summary = {
        "format": {
            "zip": "kline/{symbol}.zip",
            "files": list(periods),
            "required_files": list(REQUIRED_APP_PERIODS),
            "csv_columns": ["date", "open", "high", "low", "close", "volume"],
        },
        "included_symbols": sum(1 for r in reports if r.included),
        "qualified_symbols": sum(1 for r in reports if r.qualified),
        "total_zip_bytes": total_bytes,
        "total_zip_mb": round(total_bytes / 1024 / 1024, 3),
        "max_total_mb": args.max_total_mb,
        "target_symbols": args.target_symbols,
        "reports": [asdict(r) for r in reports],
    }
    (output_dir / "export_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    report_csv = output_dir / "export_report.csv"
    with report_csv.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(
            [
                "symbol",
                "included",
                "qualified",
                "reasons",
                "day_count",
                "week_count",
                "60min_count",
                "60min_day_coverage",
                "zip_bytes",
            ]
        )
        for r in reports:
            writer.writerow(
                [
                    r.symbol,
                    r.included,
                    r.qualified,
                    "|".join(r.reasons),
                    r.counts.get("day", 0),
                    r.counts.get("week", 0),
                    r.counts.get("60min", 0),
                    f"{r.sixty_min_day_coverage:.4f}",
                    r.zip_bytes,
                ]
            )


def main() -> int:
    args = parse_args()
    periods = tuple(p.strip() for p in args.periods.split(",") if p.strip())
    unknown = sorted(set(periods) - set(DEFAULT_PERIODS))
    if unknown:
        raise ValueError(f"Unsupported app periods: {unknown}")
    missing_required = sorted(set(REQUIRED_APP_PERIODS) - set(periods))
    if missing_required:
        raise ValueError(f"Missing required app periods: {missing_required}")

    if args.clean and args.output_dir.exists():
        shutil.rmtree(args.output_dir)
    final_dir = args.output_dir / "kline"
    temp_dir = args.output_dir / "_tmp"
    final_dir.mkdir(parents=True, exist_ok=True)
    temp_dir.mkdir(parents=True, exist_ok=True)

    QMTFetcher = import_qmt_fetcher(args.qmt_root)
    fetcher = QMTFetcher()
    codes = load_codes(args, fetcher)
    if not codes:
        raise ValueError("No symbols to export.")

    print(f"APK format: {final_dir}/{{symbol}}.zip with {','.join(periods)}.csv")
    print(f"Symbols to scan: {len(codes)}")
    print(f"Size budget: {args.max_total_mb:.1f} MB")

    reports: list[SymbolReport] = []
    total_bytes = 0
    budget_hit = False
    included_symbols: set[str] = set()
    if args.resume_existing and not args.clean:
        reports, total_bytes, included_symbols = load_existing_reports(final_dir, periods, args)
        if included_symbols:
            print(
                f"Resuming with existing ZIPs: {len(included_symbols)} included, "
                f"{total_bytes / 1024 / 1024:.2f} MB"
            )

    included = len(included_symbols)
    new_included = 0
    try:
        for idx, symbol in enumerate(codes, start=1):
            if symbol in included_symbols:
                continue
            if args.target_symbols and included >= args.target_symbols:
                print(f"Target symbol count reached: {included}/{args.target_symbols}")
                break
            if args.max_symbols and new_included >= args.max_symbols:
                break
            print(f"[{idx}/{len(codes)}] {symbol}", flush=True)
            report, total_bytes, budget_hit = process_symbol(
                fetcher=fetcher,
                symbol=symbol,
                args=args,
                periods=periods,
                temp_dir=temp_dir,
                final_dir=final_dir,
                current_total=total_bytes,
            )
            reports.append(report)
            if report.included:
                included += 1
                new_included += 1
                included_symbols.add(symbol)
                print(
                    f"  included rows day={report.counts.get('day', 0)} "
                    f"60min={report.counts.get('60min', 0)} "
                    f"zip={report.zip_bytes / 1024:.1f} KB "
                    f"total={total_bytes / 1024 / 1024:.2f} MB"
                )
            else:
                print(f"  skipped: {', '.join(report.reasons) or 'not_included'}")
            if budget_hit and not args.continue_after_budget:
                print("Size budget reached; stopping scan.")
                break
    finally:
        if hasattr(fetcher, "close"):
            fetcher.close()
        shutil.rmtree(temp_dir, ignore_errors=True)

    write_reports(args.output_dir, reports, total_bytes, periods, args)
    print()
    print(f"Included symbols: {included}")
    print(f"Generated ZIP total: {total_bytes / 1024 / 1024:.2f} MB")
    print(f"Output: {final_dir.resolve()}")
    print(f"Report: {(args.output_dir / 'export_summary.json').resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
