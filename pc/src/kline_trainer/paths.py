import os
import sys
from pathlib import Path

APP_NAME = "KLineTrainerStandalone"


def project_data_dir() -> Path:
    """行情数据根目录。

    优先级：
      1. 仓库根目录下的 ``data/``（开发 / 源码运行，与 android 版共用同一份数据）
      2. 打包后 exe 同级的 ``data/``
      3. 仅安装 pc/ 子目录时的 ``pc/data/``
    """
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent / "data"
    here = Path(__file__).resolve()
    # <repo>/pc/src/kline_trainer/paths.py -> <repo>
    repo_root = here.parents[3]
    candidates = [repo_root / "data", repo_root / "pc" / "data"]
    for candidate in candidates:
        if candidate.is_dir():
            return candidate
    return candidates[0]


DEFAULT_SOURCE_PATH = project_data_dir()


def app_data_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA")
    if base:
        path = Path(base) / APP_NAME
    else:
        path = Path.home() / f".{APP_NAME}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def static_dir() -> Path:
    bundle_root = getattr(sys, "_MEIPASS", None)
    if bundle_root:
        return Path(bundle_root) / "kline_trainer" / "static"
    return Path(__file__).resolve().parent / "static"
