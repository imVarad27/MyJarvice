import tempfile
import unittest
import sqlite3
from pathlib import Path

from action_ledger import EmailApprovalLedger


class EmailApprovalLedgerTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.ledger = EmailApprovalLedger(str(Path(self.folder.name) / "ledger.db"), ttl_seconds=120)

    def tearDown(self):
        self.folder.cleanup()

    def test_draft_can_be_claimed_only_once(self):
        draft = self.ledger.create("person@example.com", "Subject", "Body")
        state, claimed = self.ledger.claim_for_send(draft.id)
        self.assertEqual(state, "executing")
        self.assertEqual(claimed, draft)
        self.assertTrue(self.ledger.mark_sent(draft.id))
        self.assertEqual(self.ledger.claim_for_send(draft.id), ("sent", None))

    def test_discard_consumes_the_draft(self):
        draft = self.ledger.create("person@example.com", "Subject", "Body")
        self.assertEqual(self.ledger.discard(draft.id), "discarded")
        self.assertEqual(self.ledger.claim_for_send(draft.id), ("discarded", None))

    def test_failed_send_cannot_be_silently_retried(self):
        draft = self.ledger.create("person@example.com", "Subject", "Body")
        self.assertEqual(self.ledger.claim_for_send(draft.id)[0], "executing")
        self.assertTrue(self.ledger.mark_failed(draft.id, "SMTP unavailable"))
        self.assertEqual(self.ledger.claim_for_send(draft.id), ("failed", None))

    def test_tampered_payload_is_not_sent(self):
        draft = self.ledger.create("person@example.com", "Subject", "Body")
        conn = sqlite3.connect(self.ledger.db_path)
        try:
            conn.execute("UPDATE email_approvals SET body = 'Changed after review' WHERE id = ?", (draft.id,))
            conn.commit()
        finally:
            conn.close()
        self.assertEqual(self.ledger.claim_for_send(draft.id), ("invalid", None))


if __name__ == "__main__":
    unittest.main()
