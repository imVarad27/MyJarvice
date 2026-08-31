"""
JARVIS 1.0 - Smart Reminder & Task Scheduler Module
===================================================
Provides persistent SQLite reminder storage, natural language time parsing,
and background proactive alert polling.
"""

import sqlite3
import datetime
import time
import re
import os
import uuid
import threading
import logging
from typing import Dict, Any, List, Optional, Tuple, Callable

logger = logging.getLogger("JarvisScheduler")
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "jarvis.db")


def _get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=10.0)
    conn.row_factory = sqlite3.Row
    return conn


def init_scheduler_db() -> None:
    """Initializes the reminders table in SQLite."""
    with _get_conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY,
                user_id TEXT DEFAULT 'default',
                task_text TEXT NOT NULL,
                due_timestamp REAL NOT NULL,
                due_iso TEXT NOT NULL,
                created_at TEXT NOT NULL,
                is_completed INTEGER DEFAULT 0,
                is_notified INTEGER DEFAULT 0
            );
        """)
        conn.commit()


def parse_reminder_text(text: str) -> Optional[Tuple[str, datetime.datetime]]:
    """
    Parses a user instruction like:
    - 'remind me to check deployment in 15 minutes'
    - 'remind me to call Alex at 6:30 PM'
    - 'set a reminder for meeting in 2 hours'
    Returns (task_description, due_datetime) or None.
    """
    now = datetime.datetime.now()
    low = text.lower().strip()

    # 1. Relative offset: 'in X mins / hours / secs / days'
    rel_match = re.search(r'\bin\s+(\d+)\s*(mins?|minutes?|hrs?|hours?|secs?|seconds?|days?)\b', low)
    if rel_match:
        val = int(rel_match.group(1))
        unit = rel_match.group(2)
        delta = datetime.timedelta(seconds=0)
        if "sec" in unit:
            delta = datetime.timedelta(seconds=val)
        elif "min" in unit:
            delta = datetime.timedelta(minutes=val)
        elif "hr" in unit or "hour" in unit:
            delta = datetime.timedelta(hours=val)
        elif "day" in unit:
            delta = datetime.timedelta(days=val)

        due_time = now + delta

        # Clean task description
        cleaned = re.sub(
            r'\b(jarvis|hey|please|remind|me|to|set|a|reminder|for|in\s+\d+\s*(mins?|minutes?|hrs?|hours?|secs?|seconds?|days?))\b',
            '',
            text,
            flags=re.IGNORECASE
        ).strip()
        cleaned = cleaned.lstrip(",:;- ").rstrip("?.!, ").strip()
        task = cleaned if cleaned else "Scheduled Task"
        return task, due_time

    # 2. Absolute wall-clock time: 'at HH:MM AM/PM' or 'at HH AM/PM'
    abs_match = re.search(r'\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b', low)
    if abs_match:
        hour = int(abs_match.group(1))
        minute = int(abs_match.group(2)) if abs_match.group(2) else 0
        ampm = abs_match.group(3)
        if ampm == "pm" and hour < 12:
            hour += 12
        elif ampm == "am" and hour == 12:
            hour = 0

        due_time = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
        if due_time <= now:
            # If specified time already passed today, schedule for tomorrow
            due_time += datetime.timedelta(days=1)

        cleaned = re.sub(
            r'\b(jarvis|hey|please|remind|me|to|set|a|reminder|for|at\s+\d{1,2}(?::\d{2})?\s*(am|pm)?)\b',
            '',
            text,
            flags=re.IGNORECASE
        ).strip()
        cleaned = cleaned.lstrip(",:;- ").rstrip("?.!, ").strip()
        task = cleaned if cleaned else "Scheduled Task"
        return task, due_time

    return None


def add_reminder(task_text: str, due_time: datetime.datetime) -> Dict[str, Any]:
    """Saves a new reminder to SQLite."""
    init_scheduler_db()
    rem_id = str(uuid.uuid4())[:8]
    due_ts = due_time.timestamp()
    due_iso = due_time.isoformat()
    created_at = datetime.datetime.now().isoformat()

    with _get_conn() as conn:
        conn.execute("""
            INSERT INTO reminders (id, task_text, due_timestamp, due_iso, created_at, is_completed, is_notified)
            VALUES (?, ?, ?, ?, ?, 0, 0);
        """, (rem_id, task_text, due_ts, due_iso, created_at))
        conn.commit()

    logger.info("Created reminder [%s]: '%s' at %s", rem_id, task_text, due_iso)
    return {
        "id": rem_id,
        "task": task_text,
        "due_time": due_time,
        "due_iso": due_iso
    }


def get_active_reminders() -> List[Dict[str, Any]]:
    """Returns list of pending, uncompleted reminders."""
    init_scheduler_db()
    with _get_conn() as conn:
        rows = conn.execute("""
            SELECT id, task_text, due_timestamp, due_iso, created_at
            FROM reminders
            WHERE is_completed = 0
            ORDER BY due_timestamp ASC;
        """).fetchall()

    results = []
    for r in rows:
        results.append({
            "id": r["id"],
            "task": r["task_text"],
            "due_timestamp": r["due_timestamp"],
            "due_iso": r["due_iso"],
            "created_at": r["created_at"]
        })
    return results


def format_reminders_summary() -> str:
    """Formats active reminders into spoken text."""
    reminders = get_active_reminders()
    if not reminders:
        return "You have no pending reminders or scheduled tasks on your agenda, Sir."

    now = datetime.datetime.now()
    lines = [f"You have {len(reminders)} scheduled reminder{'s' if len(reminders) > 1 else ''}:"]
    for i, rem in enumerate(reminders, 1):
        due_dt = datetime.datetime.fromtimestamp(rem["due_timestamp"])
        if due_dt.date() == now.date():
            time_str = due_dt.strftime("today at %I:%M %p")
        else:
            time_str = due_dt.strftime("%A, %b %d at %I:%M %p")
        lines.append(f"{i}. '{rem['task']}' — scheduled for {time_str}.")

    return "\n".join(lines)


def clear_all_reminders() -> int:
    """Deletes all reminders."""
    init_scheduler_db()
    with _get_conn() as conn:
        cursor = conn.execute("DELETE FROM reminders;")
        conn.commit()
        return cursor.rowcount


def get_due_reminders() -> List[Dict[str, Any]]:
    """Returns reminders that are due and not yet notified."""
    init_scheduler_db()
    now_ts = datetime.datetime.now().timestamp()
    with _get_conn() as conn:
        rows = conn.execute("""
            SELECT id, task_text, due_timestamp, due_iso
            FROM reminders
            WHERE due_timestamp <= ? AND is_notified = 0 AND is_completed = 0;
        """, (now_ts,)).fetchall()

    return [{"id": r["id"], "task": r["task_text"], "due_iso": r["due_iso"]} for r in rows]


def mark_notified(reminder_ids: List[str]) -> None:
    """Marks reminders as notified."""
    if not reminder_ids:
        return
    with _get_conn() as conn:
        placeholders = ",".join("?" * len(reminder_ids))
        conn.execute(f"""
            UPDATE reminders
            SET is_notified = 1, is_completed = 1
            WHERE id IN ({placeholders});
        """, reminder_ids)
        conn.commit()


# ==============================================================================
# Background Polling Sentinel
# ==============================================================================

class SchedulerSentinel:
    def __init__(self, alert_callback: Optional[Callable[[Dict[str, Any]], None]] = None):
        self.alert_callback = alert_callback
        self._running = False
        self._thread: Optional[threading.Thread] = None

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()
        logger.info("JARVIS Scheduler Sentinel background thread started.")

    def stop(self):
        self._running = False

    def _run_loop(self):
        while self._running:
            try:
                due = get_due_reminders()
                if due:
                    notified_ids = []
                    for item in due:
                        logger.info("🚨 Reminder due: '%s' [%s]", item["task"], item["id"])
                        if self.alert_callback:
                            try:
                                self.alert_callback(item)
                            except Exception as e:
                                logger.error("Alert callback failed: %s", e)
                        notified_ids.append(item["id"])
                    mark_notified(notified_ids)
            except Exception as e:
                logger.error("Error in scheduler loop: %s", e)
            time.sleep(3)


# Singleton scheduler instance
scheduler_sentinel = SchedulerSentinel()
