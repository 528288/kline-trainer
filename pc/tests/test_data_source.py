import unittest
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from kline_trainer.config import ConfigStore
from kline_trainer.data_source import KLineDataSource
from kline_trainer.paths import DEFAULT_SOURCE_PATH
from kline_trainer.vip import MAX_FREE_TRAININGS, VipStore


class DataSourceTest(unittest.TestCase):
    def test_default_source_can_be_scanned(self):
        source = KLineDataSource(DEFAULT_SOURCE_PATH)
        status = source.status()
        self.assertTrue(status.exists, status.message)
        self.assertGreater(status.zip_count, 0)
        self.assertTrue(source.list_symbols())

    def test_default_source_can_build_session(self):
        source = KLineDataSource(DEFAULT_SOURCE_PATH)
        session = source.build_random_session(
            {
                "total_bars": 60,
                "display_count": 30,
                "train_count": 30,
            }
        )
        self.assertTrue(session["success"])
        self.assertEqual(len(session["bars"]["1d"]["display"]), 30)
        self.assertEqual(len(session["bars"]["1d"]["train"]), 30)
        self.assertTrue(Path(session["data_source"]).exists())


class VipStoreTest(unittest.TestCase):
    def test_free_training_limit_and_vip_switch(self):
        with TemporaryDirectory() as tmp:
            store = VipStore(Path(tmp) / "vip.json")
            self.assertTrue(store.can_train())
            for _ in range(MAX_FREE_TRAININGS):
                status = store.increment_training_count()
            self.assertEqual(status["training_count"], MAX_FREE_TRAININGS)
            self.assertEqual(status["remaining_free"], 0)
            self.assertFalse(store.can_train())

            status = store.set_vip(True)
            self.assertTrue(status["is_vip"])
            self.assertTrue(store.can_train())


class ConfigStoreTest(unittest.TestCase):
    def test_default_source_is_not_persisted(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "config.json"
            store = ConfigStore(path)
            config = store.save({"source_path": str(DEFAULT_SOURCE_PATH), "totalBars": 120})
            saved = json.loads(path.read_text(encoding="utf-8"))

            self.assertEqual(config["source_path"], str(DEFAULT_SOURCE_PATH))
            self.assertNotIn("source_path", saved)
            self.assertNotIn("sourcePath", saved)
            self.assertEqual(store.load()["source_path"], str(DEFAULT_SOURCE_PATH))

    def test_explicit_source_is_persisted(self):
        with TemporaryDirectory() as tmp:
            store = ConfigStore(Path(tmp) / "config.json")
            custom = str(Path(tmp) / "custom-data")
            config = store.save({"source_path": custom})
            saved = json.loads((Path(tmp) / "config.json").read_text(encoding="utf-8"))

            self.assertEqual(config["source_path"], custom)
            self.assertEqual(saved["source_path"], custom)
            self.assertEqual(store.load()["source_path"], custom)


if __name__ == "__main__":
    unittest.main()
