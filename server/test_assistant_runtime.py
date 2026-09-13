import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import assistant_runtime as runtime


class RuntimeTests(unittest.TestCase):
    def test_bounded_fast_payload(self):
        payload = runtime.model_payload([], "test", True)
        self.assertFalse(payload["think"])
        self.assertTrue(payload["stream"])
        self.assertEqual(payload["options"]["num_ctx"], 4096)
        self.assertEqual(payload["options"]["num_predict"], 512)

    def test_only_time_sensitive_questions_search(self):
        for text in ["What is gravity?", "Tell me about Python", "Who is Sherlock Holmes?"]:
            self.assertFalse(runtime.needs_web(text), text)
        for text in ["latest news", "weather in Pune", "search the web for Vosk"]:
            self.assertTrue(runtime.needs_web(text), text)

    def test_stream_ignores_thinking_and_accumulates_text(self):
        chunks = [{"message": {"thinking": "private"}}, {"message": {"content": "Hello"}},
                  {"message": {"content": " there"}, "done": True}]
        response = io.BytesIO(b"\n".join(json.dumps(c).encode() for c in chunks))
        updates = []
        with patch("urllib.request.urlopen", return_value=response):
            self.assertEqual(runtime.generate([], "test", "http://localhost/chat", 10, updates.append), "Hello there")
        self.assertEqual(updates[-1], "Hello there")
        self.assertNotIn("private", "".join(updates))

    def test_stream_errors_surface(self):
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b'{"error":"no model"}\n')):
            with self.assertRaisesRegex(RuntimeError, "no model"):
                runtime.generate([], "test", "http://localhost/chat", 10, lambda _: None)

    def test_tasks_persist_and_complete_exact_id(self):
        with tempfile.TemporaryDirectory() as folder:
            db = str(Path(folder) / "test.db")
            self.assertIsNone(runtime.task_command("Can you discuss tasks?", db))
            self.assertFalse(Path(db).exists())
            self.assertIn("#1", runtime.task_command("add task buy groceries", db))
            self.assertIn("buy groceries", runtime.task_command("plan my day", db))
            self.assertIn("couldn't find", runtime.task_command("complete task 9", db))
            self.assertIn("complete", runtime.task_command("complete task 1", db))
            self.assertIn("no open tasks", runtime.task_command("show my tasks", db))


if __name__ == "__main__":
    unittest.main()
