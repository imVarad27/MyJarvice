"""Bounded Ollama inference and typed personal task storage."""
import datetime
import json
import os
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
            "num_ctx": int(os.environ.get("JARVIS_CONTEXT_SIZE", "8192")),
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


def generate_message(messages, model, url, timeout, tools=None):
    """Return one complete Ollama message, including native tool calls."""
    payload = model_payload(messages, model, stream=False)
    if tools:
        payload["tools"] = tools
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        result = json.load(response)
    if result.get("error"):
        raise RuntimeError(result["error"])
    message = result.get("message")
    if not isinstance(message, dict):
        raise RuntimeError("Ollama returned no message")
    return message


def task_tool(name, arguments, db_path):
    """Validated operations; callers supply tool names, never raw user phrases."""
    import model_tool_router
    decision = model_tool_router.decision_from_message({"tool_calls": [
        {"function": {"name": name, "arguments": arguments}}
    ]})
    if decision is None or name not in {"add_task", "complete_task", "list_tasks"}:
        raise ValueError("Invalid task operation")
    with closing(sqlite3.connect(db_path, timeout=10)) as db, db:
        db.execute("CREATE TABLE IF NOT EXISTS assistant_tasks (id INTEGER PRIMARY KEY, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)")
        if name == "add_task":
            title = decision.argument
            cursor = db.execute("INSERT INTO assistant_tasks(title,created_at) VALUES (?,?)", (title, datetime.datetime.now().isoformat()))
            return f"Added PC task #{cursor.lastrowid}: {title}."
        if name == "complete_task":
            task_id = decision.arguments["id"]
            cursor = db.execute("UPDATE assistant_tasks SET done=1 WHERE id=? AND done=0", (task_id,))
            if not cursor.rowcount:
                raise LookupError(f"I couldn't find an open PC task #{task_id}. Nothing changed.")
            return f"PC task #{task_id} is complete."
        rows = db.execute("SELECT id,title FROM assistant_tasks WHERE done=0 ORDER BY id LIMIT 30").fetchall()
        if not rows:
            return "You have no open PC tasks."
        return "Your open tasks:\n" + "\n".join(f"#{key}: {title}" for key, title in rows)
