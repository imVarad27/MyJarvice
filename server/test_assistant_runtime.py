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
        self.assertEqual(payload["options"]["num_ctx"], 8192)
        self.assertEqual(payload["options"]["num_predict"], 512)

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

    def test_native_tool_message_is_preserved(self):
        message = {"role": "assistant", "content": "", "tool_calls": [
            {"function": {"name": "phone_status", "arguments": {}}}
        ]}
        response = io.BytesIO(json.dumps({"message": message}).encode())
        with patch("urllib.request.urlopen", return_value=response):
            self.assertEqual(
                message,
                runtime.generate_message([], "test", "http://localhost/chat", 10, tools=[{"type": "function"}]),
            )

    def test_tasks_persist_and_complete_exact_id(self):
        with tempfile.TemporaryDirectory() as folder:
            db = str(Path(folder) / "test.db")
            with self.assertRaises(ValueError):
                runtime.task_tool("Can you discuss tasks?", {}, db)
            self.assertFalse(Path(db).exists())
            self.assertIn("#1", runtime.task_tool("add_task", {"title": "buy groceries"}, db))
            self.assertIn("buy groceries", runtime.task_tool("list_tasks", {}, db))
            with self.assertRaisesRegex(LookupError, "couldn't find"):
                runtime.task_tool("complete_task", {"id": 9}, db)
            self.assertIn("complete", runtime.task_tool("complete_task", {"id": 1}, db))
            self.assertIn("no open PC tasks", runtime.task_tool("list_tasks", {}, db))


if __name__ == "__main__":
    unittest.main()
