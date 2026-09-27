"""Durable, one-use approvals for consequential Jarvis actions.

The language model never talks to SMTP directly.  This ledger keeps the exact
draft payload and allows exactly one state transition from awaiting approval to
executing.  It is intentionally independent of FastAPI so it can be tested
without starting the host server.
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import secrets
import sqlite3
import uuid
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class EmailDraft:
    id: str
    to: str
    subject: str
    body: str
    expires_at: str


class EmailApprovalLedger:
    """SQLite-backed email approval state machine.

    Valid states are awaiting_approval -> executing -> sent/failed, or
    awaiting_approval -> discarded/expired.  No state can move back to
    awaiting_approval, so an approval cannot be replayed after a restart.
    """

    def __init__(self, db_path: str, ttl_seconds: int = 900) -> None:
        self.db_path = db_path
        self.ttl_seconds = max(60, ttl_seconds)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        conn = self._connect()
        try:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS email_approvals (
                    id TEXT PRIMARY KEY,
                    recipient TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    body TEXT NOT NULL,
                    payload_hash TEXT NOT NULL,
                    state TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    approved_at TEXT,
                    resolved_at TEXT,
                    last_error TEXT
                )
                """
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_email_approvals_state "
                "ON email_approvals(state, expires_at)"
            )
            conn.commit()
        finally:
            conn.close()

    @staticmethod
    def _now() -> dt.datetime:
        return dt.datetime.now(dt.timezone.utc)

    @staticmethod
    def _timestamp(value: dt.datetime) -> str:
        return value.isoformat(timespec="seconds")

    @staticmethod
    def _payload_hash(to: str, subject: str, body: str) -> str:
        payload = json.dumps(
            {"to": to, "subject": subject, "body": body},
            sort_keys=True,
            separators=(",", ":"),
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def create(self, to: str, subject: str, body: str) -> EmailDraft:
        now = self._now()
        expires_at = now + dt.timedelta(seconds=self.ttl_seconds)
        draft = EmailDraft(
            id=uuid.uuid4().hex,
            to=to,
            subject=subject,
            body=body,
            expires_at=self._timestamp(expires_at),
        )
        conn = self._connect()
        try:
            conn.execute(
                """
                INSERT INTO email_approvals
                    (id, recipient, subject, body, payload_hash, state, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, 'awaiting_approval', ?, ?)
                """,
                (
                    draft.id,
                    draft.to,
                    draft.subject,
                    draft.body,
                    self._payload_hash(draft.to, draft.subject, draft.body),
                    self._timestamp(now),
                    draft.expires_at,
                ),
            )
            conn.commit()
        finally:
            conn.close()
        return draft

    def discard(self, draft_id: str) -> str:
        """Discard an awaiting draft. Returns a stable result code."""
        now = self._timestamp(self._now())
        conn = self._connect()
        try:
            conn.execute(
                """UPDATE email_approvals SET state = 'expired', resolved_at = ?
                   WHERE id = ? AND state = 'awaiting_approval' AND expires_at <= ?""",
                (now, draft_id, now),
            )
            changed = conn.execute(
                """UPDATE email_approvals SET state = 'discarded', resolved_at = ?
                   WHERE id = ? AND state = 'awaiting_approval' AND expires_at > ?""",
                (now, draft_id, now),
            ).rowcount
            conn.commit()
            return "discarded" if changed else self._state(conn, draft_id)
        finally:
            conn.close()

    def claim_for_send(self, draft_id: str) -> tuple[str, Optional[EmailDraft]]:
        """Atomically claim an approved draft for the sole SMTP attempt."""
        now = self._timestamp(self._now())
        conn = self._connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            conn.execute(
                """UPDATE email_approvals SET state = 'expired', resolved_at = ?
                   WHERE id = ? AND state = 'awaiting_approval' AND expires_at <= ?""",
                (now, draft_id, now),
            )
            changed = conn.execute(
                """UPDATE email_approvals
                   SET state = 'executing', approved_at = ?
                   WHERE id = ? AND state = 'awaiting_approval' AND expires_at > ?""",
                (now, draft_id, now),
            ).rowcount
            if not changed:
                conn.commit()
                return self._state(conn, draft_id), None
            row = conn.execute(
                "SELECT id, recipient, subject, body, payload_hash, expires_at "
                "FROM email_approvals WHERE id = ?",
                (draft_id,),
            ).fetchone()
            assert row is not None
            if not secrets.compare_digest(
                row["payload_hash"],
                self._payload_hash(row["recipient"], row["subject"], row["body"]),
            ):
                conn.execute(
                    "UPDATE email_approvals SET state = 'failed', resolved_at = ?, last_error = ? WHERE id = ?",
                    (now, "Draft integrity check failed", draft_id),
                )
                conn.commit()
                return "invalid", None
            conn.commit()
            return "executing", EmailDraft(
                id=row["id"], to=row["recipient"], subject=row["subject"],
                body=row["body"], expires_at=row["expires_at"],
            )
        finally:
            conn.close()

    def mark_sent(self, draft_id: str) -> bool:
        return self._finish(draft_id, "sent")

    def mark_failed(self, draft_id: str, error: str) -> bool:
        return self._finish(draft_id, "failed", error[:500])

    def _finish(self, draft_id: str, state: str, error: Optional[str] = None) -> bool:
        conn = self._connect()
        try:
            changed = conn.execute(
                """UPDATE email_approvals SET state = ?, resolved_at = ?, last_error = ?
                   WHERE id = ? AND state = 'executing'""",
                (state, self._timestamp(self._now()), error, draft_id),
            ).rowcount
            conn.commit()
            return bool(changed)
        finally:
            conn.close()

    @staticmethod
    def _state(conn: sqlite3.Connection, draft_id: str) -> str:
        row = conn.execute("SELECT state FROM email_approvals WHERE id = ?", (draft_id,)).fetchone()
        return row["state"] if row else "missing"
