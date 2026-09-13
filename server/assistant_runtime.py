"""Bounded local inference and deterministic personal task commands."""
import datetime
import json
import os
import re
import sqlite3
import time
import urllib.request
from contextlib import closing


def model_payload(messages, model, stream=False):
    return {
        "model": model, "messages": messages, "stream": stream,
        # Gemma's hidden reasoning can consume the entire short-answer budget.
        "think": False,
        "keep_alive": os.environ.get("JARVIS_KEEP_ALIVE", "30m"),
        "options": {
            "num_ctx": int(os.environ.get("JARVIS_CONTEXT_SIZE", "4096")),
            "num_predict": int(os.environ.get("JARVIS_MAX_TOKENS", "512")),
            "temperature": 0.6,
        },
    }


def generate(messages, model, url, timeout, on_text=None):
    payload = model_payload(messages, model, stream=on_text is not None)
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        if on_text is None:
            result = json.load(response)
            if result.get("error"):
                raise RuntimeError(result["error"])
            return result.get("message", {}).get("content", "").strip()
        parts = []
        last_emit = 0.0
        for line in response:
            if not line.strip():
                continue
            result = json.loads(line)
            if result.get("error"):
                raise RuntimeError(result["error"])
            content = result.get("message", {}).get("content", "")
            if content:
                parts.append(content)
                if time.monotonic() - last_emit >= 0.08:
                    on_text("".join(parts))
                    last_emit = time.monotonic()
            if result.get("done"):
                break
        text = "".join(parts).strip()
        if text:
            on_text(text)
        return text


def needs_web(text):
    # General knowledge should not wait for a network lookup.
    return bool(re.search(r"\b(weather|forecast|news|headlines?|latest|current|today's|"
                          r"stock price|price of|search (?:the web|online)|look up)\b", text, re.I))


def task_command(text, db_path):
    """Explicit add/list/complete commands; unrelated conversation is untouched."""
    clean = text.strip()
    add = re.fullmatch(r"(?:add (?:a )?task|todo|to-do)\s*[:\-]?\s+(.{1,500})", clean, re.I)
    complete = re.fullmatch(r"(?:complete|finish) task\s+#?(\d+)[.!]?", clean, re.I)
    show = re.fullmatch(r"(?:show|list)(?: my)? tasks[.!]?|my tasks[.!]?|plan my day[.!]?", clean, re.I)
    if not (add or complete or show):
        return None
    with closing(sqlite3.connect(db_path, timeout=10)) as db, db:
        db.execute("CREATE TABLE IF NOT EXISTS assistant_tasks (id INTEGER PRIMARY KEY, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)")
        if add:
            title = add[1].strip()
            cursor = db.execute("INSERT INTO assistant_tasks(title,created_at) VALUES (?,?)", (title, datetime.datetime.now().isoformat()))
            return f"Added task #{cursor.lastrowid}: {title}. Say ‘complete task {cursor.lastrowid}’ when it's done."
        if complete:
            cursor = db.execute("UPDATE assistant_tasks SET done=1 WHERE id=? AND done=0", (int(complete[1]),))
            return f"Task #{complete[1]} is complete." if cursor.rowcount else f"I couldn't find an open task #{complete[1]}."
        rows = db.execute("SELECT id,title FROM assistant_tasks WHERE done=0 ORDER BY id LIMIT 30").fetchall()
        if not rows:
            return "You have no open tasks. Say ‘add task finish my assignment’ to save one."
        return "Your open tasks:\n" + "\n".join(f"#{key}: {title}" for key, title in rows)
