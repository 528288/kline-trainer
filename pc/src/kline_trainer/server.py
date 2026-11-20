import argparse
import json
import mimetypes
import socket
import threading
import webbrowser
from dataclasses import asdict
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

from .config import ConfigStore
from .data_source import KLineDataSource
from .paths import app_data_dir, static_dir
from .storage import RunStorage
from .vip import VipStore


class AppContext:
    def __init__(self, config_store: ConfigStore, run_storage: RunStorage, vip_store: VipStore):
        self.config_store = config_store
        self.run_storage = run_storage
        self.vip_store = vip_store

    def config(self) -> dict:
        return self.config_store.load()

    def data_source(self) -> KLineDataSource:
        return KLineDataSource(self.config()["source_path"])


class KLineRequestHandler(BaseHTTPRequestHandler):
    server: "KLineHTTPServer"

    def do_GET(self):
        parsed = urlparse(self.path)
        try:
            if parsed.path == "/api/health":
                self._json({"success": True})
            elif parsed.path in {"/api/config", "/api/kline-trainer/config"}:
                self._json(self._config_response())
            elif parsed.path == "/api/symbols":
                self._handle_symbols()
            elif parsed.path in {"/api/save-ids", "/api/kline-trainer/save-ids"}:
                self._json({"success": True, "ids": self.server.app.run_storage.list_group_ids()})
            elif parsed.path in {"/api/vip", "/api/kline-trainer/vip"}:
                self._json({"success": True, "vip": self.server.app.vip_store.status()})
            elif parsed.path in {"/api/runs", "/api/kline-trainer/runs"}:
                self._handle_list_runs(parsed.query)
            elif parsed.path.startswith("/api/runs/") or parsed.path.startswith("/api/kline-trainer/runs/"):
                self._handle_get_run(parsed.path)
            else:
                self._serve_static(parsed.path)
        except Exception as exc:
            self._json({"success": False, "message": str(exc)}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def do_POST(self):
        parsed = urlparse(self.path)
        try:
            if parsed.path in {"/api/config", "/api/kline-trainer/config"}:
                payload = self._read_json()
                config = self.server.app.config_store.save(payload)
                self._json({**self._config_response(config), "success": True})
            elif parsed.path in {"/api/session", "/api/kline-trainer/session"}:
                self._handle_start_session()
            elif parsed.path in {"/api/vip", "/api/kline-trainer/vip"}:
                self._handle_update_vip()
            elif parsed.path in {"/api/runs", "/api/kline-trainer/runs"}:
                self._handle_save_run()
            else:
                self._json({"success": False, "message": "接口不存在"}, HTTPStatus.NOT_FOUND)
        except ValueError as exc:
            self._json({"success": False, "message": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:
            self._json({"success": False, "message": str(exc)}, HTTPStatus.INTERNAL_SERVER_ERROR)

    def log_message(self, fmt, *args):
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

    def _config_response(self, config: dict | None = None) -> dict:
        config = config or self.server.app.config()
        status = self.server.app.data_source().status()
        return {
            "success": True,
            "config": config,
            "data_status": asdict(status),
        }

    def _handle_symbols(self):
        data_source = self.server.app.data_source()
        config = self.server.app.config()
        min_count = int(config["total_bars"])
        symbols = data_source.trainable_symbols(min_count)
        self._json(
            {
                "success": True,
                "symbols": symbols[:1000],
                "count": len(symbols),
                "required_day_bars": min_count,
                "data_status": asdict(data_source.status()),
            }
        )

    def _handle_start_session(self):
        vip = self.server.app.vip_store
        if not vip.can_train():
            self._json(
                {
                    "success": False,
                    "message": "免费训练次数已用完，请在支持/VIP中开启支持者模式后继续训练。",
                    "vip": vip.status(),
                }
            )
            return

        config = self.server.app.config()
        session = self.server.app.data_source().build_random_session(config)
        if not vip.is_vip():
            session["vip"] = vip.increment_training_count()
        else:
            session["vip"] = vip.status()
        self._json(session)

    def _handle_update_vip(self):
        payload = self._read_json()
        vip = self.server.app.vip_store
        if "is_vip" in payload:
            status = vip.set_vip(bool(payload.get("is_vip")))
        else:
            status = vip.status()
        if payload.get("reset_count"):
            status = vip.reset_count()
        self._json({"success": True, "vip": status})

    def _handle_save_run(self):
        payload = self._read_json()
        group_id = (payload.get("group_id") or "").strip()
        run_payload = payload.get("payload") or {}
        record = self.server.app.run_storage.save_run(group_id, run_payload)
        self._json({"success": True, "record": record})

    def _handle_list_runs(self, query: str):
        params = parse_qs(query)
        group_id = (params.get("group_id") or [""])[0].strip()
        if not group_id:
            self._json({"success": False, "message": "保存ID不能为空"}, HTTPStatus.BAD_REQUEST)
            return
        runs = self.server.app.run_storage.list_runs(group_id)
        self._json({"success": True, "runs": runs})

    def _handle_get_run(self, path: str):
        run_id = unquote(path.rsplit("/", 1)[-1])
        record = self.server.app.run_storage.get_run(run_id)
        if not record:
            self._json({"success": False, "message": "训练记录不存在"}, HTTPStatus.NOT_FOUND)
            return
        self._json({"success": True, "record": record})

    def _serve_static(self, path: str):
        root = static_dir().resolve()
        if path in {"", "/"}:
            target = root / "index.html"
        else:
            safe_parts = [part for part in Path(unquote(path.lstrip("/"))).parts if part not in {"..", ""}]
            target = root.joinpath(*safe_parts)
        resolved = target.resolve()
        if not resolved.exists() or not resolved.is_file() or (root not in resolved.parents and resolved != root):
            self._json({"success": False, "message": "资源不存在"}, HTTPStatus.NOT_FOUND)
            return

        content = resolved.read_bytes()
        content_type = mimetypes.guess_type(str(resolved))[0] or "application/octet-stream"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8"))

    def _json(self, payload: dict, status=HTTPStatus.OK):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)


class KLineHTTPServer(ThreadingHTTPServer):
    def __init__(self, server_address, handler_class, app: AppContext):
        super().__init__(server_address, handler_class)
        self.app = app


def main(argv: list[str] | None = None):
    parser = argparse.ArgumentParser(description="KLine Trainer Standalone")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--source", default="")
    parser.add_argument("--data-dir", default="")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args(argv)

    data_dir = Path(args.data_dir) if args.data_dir else app_data_dir()
    config_store = ConfigStore(data_dir / "config.json")
    if args.source:
        config_store.save({"source_path": args.source})
    run_storage = RunStorage(data_dir / "kline_trainer.sqlite3")
    vip_store = VipStore(data_dir / "vip_prefs.json")
    app = AppContext(config_store, run_storage, vip_store)

    port = _available_port(args.host, args.port)
    server = KLineHTTPServer((args.host, port), KLineRequestHandler, app)
    url = f"http://{args.host}:{port}/"

    print("KLine Trainer Standalone")
    print(f"URL: {url}")
    print(f"Data dir: {data_dir}")
    print(f"Source: {app.config()['source_path']}")
    if not args.no_browser:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping...")
    finally:
        run_storage.close()
        server.server_close()


def _available_port(host: str, preferred: int) -> int:
    for port in range(preferred, preferred + 100):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            try:
                sock.bind((host, port))
            except OSError:
                continue
            return port
    raise RuntimeError("没有可用端口")
