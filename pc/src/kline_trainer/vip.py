import json
import threading
from pathlib import Path


MAX_FREE_TRAININGS = 66
VIP_PAYMENT_URL = "https://qr.alipay.com/tsx11426f7zf2uqre8gdoe0"
VIP_PAYMENT_FALLBACK_URL = "https://www.alipay.com/"


class VipStore:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def status(self) -> dict:
        data = self._load()
        is_vip = bool(data.get("is_vip")) if data.get("is_vip_confirmed_v2") else False
        count = _safe_int(data.get("training_count"), 0)
        remaining = max(0, MAX_FREE_TRAININGS - count)
        return {
            "is_vip": is_vip,
            "is_vip_confirmed": bool(data.get("is_vip_confirmed_v2")),
            "training_count": count,
            "max_free_trainings": MAX_FREE_TRAININGS,
            "remaining_free": remaining,
            "can_train": is_vip or count < MAX_FREE_TRAININGS,
            "payment_url": VIP_PAYMENT_URL,
            "payment_fallback_url": VIP_PAYMENT_FALLBACK_URL,
        }

    def can_train(self) -> bool:
        return bool(self.status()["can_train"])

    def is_vip(self) -> bool:
        return bool(self.status()["is_vip"])

    def set_vip(self, value: bool) -> dict:
        with self._lock:
            data = self._load_unlocked()
            data["is_vip"] = bool(value)
            data["is_vip_confirmed_v2"] = True
            self._save_unlocked(data)
        return self.status()

    def increment_training_count(self) -> dict:
        with self._lock:
            data = self._load_unlocked()
            data["training_count"] = _safe_int(data.get("training_count"), 0) + 1
            self._save_unlocked(data)
        return self.status()

    def reset_count(self) -> dict:
        with self._lock:
            data = self._load_unlocked()
            data["training_count"] = 0
            self._save_unlocked(data)
        return self.status()

    def _load(self) -> dict:
        with self._lock:
            return self._load_unlocked()

    def _load_unlocked(self) -> dict:
        if not self.path.exists():
            return {}
        try:
            value = json.loads(self.path.read_text(encoding="utf-8"))
            return value if isinstance(value, dict) else {}
        except Exception:
            return {}

    def _save_unlocked(self, data: dict):
        self.path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _safe_int(value, default=0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (TypeError, ValueError):
        return default

