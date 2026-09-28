import tempfile
import unittest
from pathlib import Path

from action_audit import ActionAuditLog


class ActionAuditLogTests(unittest.TestCase):
    def test_keeps_recent_metadata_without_plaintext_details(self):
        with tempfile.TemporaryDirectory() as folder:
            db_path = str(Path(folder) / "audit.db")
            audit = ActionAuditLog(db_path, max_entries=100)
            audit.record("email.send", "sent", "Email sent after approval", {"recipient": "person@example.com"})
            events = audit.recent()
            self.assertEqual(events[0]["action_type"], "email.send")
            self.assertEqual(events[0]["outcome"], "sent")
            self.assertNotIn("person@example.com", str(events))

    def test_keeps_bounded_history(self):
        with tempfile.TemporaryDirectory() as folder:
            audit = ActionAuditLog(str(Path(folder) / "audit.db"), max_entries=100)
            for index in range(105):
                audit.record("file.browse", "completed", f"Browse {index}")
            self.assertEqual(len(audit.recent(100)), 100)
            self.assertEqual(audit.recent(1)[0]["summary"], "Browse 104")


if __name__ == "__main__":
    unittest.main()
