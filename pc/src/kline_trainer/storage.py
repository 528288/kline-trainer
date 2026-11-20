import json
import sqlite3
import uuid
from datetime import datetime
from pathlib import Path


class RunStorage:
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._init_tables()

    def close(self):
        self.conn.close()

    def _init_tables(self):
        self.conn.execute(
            """
            CREATE TABLE IF NOT EXISTS kline_trainer_runs (
                id TEXT PRIMARY KEY,
                group_id TEXT NOT NULL,
                stock_code TEXT,
                stock_name TEXT,
                trained_at TEXT NOT NULL,
                advance_mode TEXT,
                train_count INTEGER,
                trade_count INTEGER,
                total_pnl REAL,
                cumulative_pnl REAL,
                win_rate REAL,
                payload TEXT NOT NULL
            )
            """
        )
        self.conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_kline_trainer_runs_group ON kline_trainer_runs(group_id)"
        )
        self.conn.commit()

    def list_group_ids(self) -> list[str]:
        rows = self.conn.execute(
            """
            SELECT group_id
            FROM kline_trainer_runs
            GROUP BY group_id
            ORDER BY MAX(trained_at) DESC
            """
        ).fetchall()
        return [row["group_id"] for row in rows]

    def save_run(self, group_id: str, payload: dict) -> dict:
        group_id = (group_id or "").strip()
        if not group_id:
            raise ValueError("保存ID不能为空")
        if not isinstance(payload, dict):
            raise ValueError("训练记录内容必须是对象")

        run_id = f"ktr_{uuid.uuid4().hex}"
        summary = _extract_summary(payload)
        trained_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.conn.execute(
            """
            INSERT INTO kline_trainer_runs (
                id, group_id, stock_code, stock_name, trained_at, advance_mode,
                train_count, trade_count, total_pnl, cumulative_pnl, win_rate, payload
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                run_id,
                group_id,
                summary["stock_code"],
                summary["stock_name"],
                trained_at,
                summary["advance_mode"],
                summary["train_count"],
                summary["trade_count"],
                summary["total_pnl"],
                summary["cumulative_pnl"],
                summary["win_rate"],
                json.dumps(payload, ensure_ascii=False),
            ],
        )
        self.conn.commit()
        return self.get_run_summary(run_id)

    def get_run_summary(self, run_id: str) -> dict | None:
        row = self.conn.execute(
            """
            SELECT id, group_id, stock_code, stock_name, trained_at, advance_mode,
                   train_count, trade_count, total_pnl, cumulative_pnl, win_rate
            FROM kline_trainer_runs
            WHERE id = ?
            """,
            [run_id],
        ).fetchone()
        return _row_to_summary(row) if row else None

    def list_runs(self, group_id: str) -> list[dict]:
        group_id = (group_id or "").strip()
        if not group_id:
            return []
        rows = self.conn.execute(
            """
            SELECT id, group_id, stock_code, stock_name, trained_at, advance_mode,
                   train_count, trade_count, total_pnl, cumulative_pnl, win_rate
            FROM kline_trainer_runs
            WHERE group_id = ?
            ORDER BY trained_at DESC
            """,
            [group_id],
        ).fetchall()
        return [_row_to_summary(row) for row in rows]

    def get_run(self, run_id: str) -> dict | None:
        row = self.conn.execute(
            """
            SELECT id, group_id, stock_code, stock_name, trained_at, advance_mode,
                   train_count, trade_count, total_pnl, cumulative_pnl, win_rate, payload
            FROM kline_trainer_runs
            WHERE id = ?
            """,
            [run_id],
        ).fetchone()
        if not row:
            return None
        result = _row_to_summary(row)
        result["payload"] = json.loads(row["payload"])
        return result


def _extract_summary(payload: dict) -> dict:
    session = payload.get("session") or {}
    stats = payload.get("stats") or {}
    config = payload.get("config") or {}
    return {
        "stock_code": payload.get("stockCode") or payload.get("stock_code") or session.get("stock_code"),
        "stock_name": payload.get("stockName") or payload.get("stock_name") or session.get("stock_name"),
        "advance_mode": payload.get("advanceMode") or config.get("advanceMode") or config.get("advance_mode"),
        "train_count": _safe_int(payload.get("trainCount") or session.get("train_count")),
        "trade_count": _safe_int(stats.get("tradeCount")),
        "total_pnl": _safe_float(stats.get("totalPnl")),
        "cumulative_pnl": _safe_float(stats.get("cumulativePnl")),
        "win_rate": _safe_float(stats.get("winRate")),
    }


def _row_to_summary(row) -> dict:
    return {
        "id": row["id"],
        "group_id": row["group_id"],
        "stock_code": row["stock_code"],
        "stock_name": row["stock_name"],
        "trained_at": row["trained_at"],
        "advance_mode": row["advance_mode"],
        "train_count": row["train_count"] or 0,
        "trade_count": row["trade_count"] or 0,
        "total_pnl": _safe_float(row["total_pnl"]),
        "cumulative_pnl": _safe_float(row["cumulative_pnl"]),
        "win_rate": _safe_float(row["win_rate"]),
    }


def _safe_float(value, default=0.0) -> float:
    try:
        if value is None:
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _safe_int(value, default=0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (TypeError, ValueError):
        return default

