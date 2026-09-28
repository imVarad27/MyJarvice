"""Privacy-minimised, durable audit records for Jarvis actions."""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import sqlite3
from typing import Any, Dict, List, Optional


class ActionAuditLog:
    """Store action outcomes without retaining sensitive action payloads."""

    def __init__(self, db_path: str, max_entries: int = 2_000) -> None:
        self.db_path = db_path
        self.max_entries = max(100, max_entries)
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
                CREATE TABLE IF NOT EXISTS action_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    created_at TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    outcome TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    detail_hash TEXT
                )
                """
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_action_audit_created ON action_audit(created_at DESC)")
            conn.commit()
        finally:
            conn.close()

    @staticmethod
    def _now() -> str:
        return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")

    @staticmethod
    def _hash_details(details: Optional[Dict[str, Any]]) -> Optional[str]:
        if not details:
            return None
        encoded = json.dumps(details, sort_keys=True, separators=(",", ":"), default=str)
        return hashlib.sha256(encoded.encode("utf-8")).hexdigest()

    def record(
        self,
        action_type: str,
        outcome: str,
        summary: str,
        details: Optional[Dict[str, Any]] = None,
    ) -> None:
        """Record metadata only; details are represented by an irreversible hash."""
        conn = self._connect()
        try:
            conn.execute(
                "INSERT INTO action_audit(created_at, action_type, outcome, summary, detail_hash) "
                "VALUES (?, ?, ?, ?, ?)",
                (self._now(), action_type[:80], outcome[:40], summary[:240], self._hash_details(details)),
            )
            conn.execute(
                "DELETE FROM action_audit WHERE id NOT IN "
                "(SELECT id FROM action_audit ORDER BY id DESC LIMIT ?)",
                (self.max_entries,),
            )
            conn.commit()
        finally:
            conn.close()

    def recent(self, limit: int = 50) -> List[Dict[str, Any]]:
        limit = min(max(1, int(limit)), 100)
        conn = self._connect()
        try:
            rows = conn.execute(
                "SELECT id, created_at, action_type, outcome, summary "
                "FROM action_audit ORDER BY id DESC LIMIT ?",
                (limit,),
            ).fetchall()
            return [dict(row) for row in rows]
        finally:
            conn.close()
