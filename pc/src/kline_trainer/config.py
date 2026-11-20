import json
from pathlib import Path

from .paths import DEFAULT_SOURCE_PATH


DEFAULT_CONFIG = {
    "source_path": str(DEFAULT_SOURCE_PATH),
    "totalBars": 300,
    "displayCount": 150,
    "trainCount": 150,
    "advanceMode": "two_step",
    "autoInterval": 2.0,
}


class ConfigStore:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def load(self) -> dict:
        data = {}
        if self.path.exists():
            try:
                data = json.loads(self.path.read_text(encoding="utf-8"))
            except Exception:
                data = {}
        return self.normalize({**DEFAULT_CONFIG, **data})

    def save(self, patch: dict) -> dict:
        current = self.load()
        allowed = {
            "source_path",
            "sourcePath",
            "totalBars",
            "displayCount",
            "trainCount",
            "advanceMode",
            "autoInterval",
            "total_bars",
            "display_count",
            "train_count",
            "advance_mode",
            "auto_interval",
        }
        next_config = {**current}
        for key, value in (patch or {}).items():
            if key in allowed:
                next_config[key] = value
        next_config = self.normalize(next_config)
        storage_config = self._storage_payload(next_config)
        self.path.write_text(
            json.dumps(storage_config, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return next_config

    @staticmethod
    def normalize(config: dict) -> dict:
        result = {**DEFAULT_CONFIG, **(config or {})}

        source = result.get("source_path") or result.get("sourcePath") or DEFAULT_SOURCE_PATH
        total = _coerce_int(_first(result, "totalBars", "total_bars"), 300, 30, 5000)
        display = _coerce_int(_first(result, "displayCount", "display_count"), 150, 10, 4000)
        train = _coerce_int(_first(result, "trainCount", "train_count"), 150, 1, 4000)

        needed = display + train
        if total < needed:
            total = needed

        mode = str(_first(result, "advanceMode", "advance_mode") or "two_step").lower()
        if mode == "single_step":
            mode = "one_step"
        mode = mode if mode in {"one_step", "two_step"} else "two_step"
        interval = _coerce_float(_first(result, "autoInterval", "auto_interval"), 2.0, 0.2, 30.0)

        # Return both naming styles: the copied Vue page reads camelCase, while
        # backend/session code and older smoke tests can use snake_case.
        return {
            "source_path": str(source),
            "sourcePath": str(source),
            "totalBars": total,
            "displayCount": display,
            "trainCount": train,
            "advanceMode": mode,
            "autoInterval": interval,
            "total_bars": total,
            "display_count": display,
            "train_count": train,
            "advance_mode": mode,
            "auto_interval": interval,
        }

    @staticmethod
    def _storage_payload(config: dict) -> dict:
        payload = {**config}
        if _same_path(payload.get("source_path"), DEFAULT_SOURCE_PATH):
            payload.pop("source_path", None)
            payload.pop("sourcePath", None)
        return payload


def _first(data: dict, *keys):
    for key in keys:
        if key in data and data[key] is not None:
            return data[key]
    return None


def _same_path(left, right) -> bool:
    return str(left).replace("/", "\\").rstrip("\\").casefold() == str(right).replace("/", "\\").rstrip("\\").casefold()


def _coerce_int(value, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(value)
    except (TypeError, ValueError):
        value = default
    return max(minimum, min(maximum, value))


def _coerce_float(value, default: float, minimum: float, maximum: float) -> float:
    try:
        value = float(value)
    except (TypeError, ValueError):
        value = default
    return max(minimum, min(maximum, value))
