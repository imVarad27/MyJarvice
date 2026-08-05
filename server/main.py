import asyncio
import json
import logging
import datetime
import os
import re
import sqlite3
import urllib.request
import urllib.error
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("MyJarviceServer")

app = FastAPI(title="MyJarvice Host Server", version="2.0.0")

# --- Configuration ---
OLLAMA_URL = "http://localhost:11434/api/chat"
DEFAULT_MODEL = "gemma4-e4b"          # Local Ollama model
OLLAMA_TIMEOUT = 120                   # seconds — generous so the real model always answers (Phase 1 fix)
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "jarvice.db")
MAX_HISTORY_TURNS = 8                  # how many past messages to keep in context per session

# --- Seed data used only on first run (afterwards memory lives in SQLite) ---
SEED_MEMORY = [
    ("user", "name", "Sir / Creator"),
    ("preference", "coffee", "Prefers espresso with light oat milk"),
    ("schedule", "daily_standup", "Daily team standup at 10:00 AM"),
    ("schedule", "gym", "Gym workout scheduled at 6:30 PM"),
    ("contact", "emergency", "Primary emergency contact is Alex"),
    ("note", "project", "MyJarvice project phase 1 local deployment in progress"),
]

# --- In-Memory IoT Device State (Phase 3 will make these real device actions) ---
IOT_DEVICES = {
    "living_room_light": {"type": "light", "state": "OFF", "brightness": 80, "color": "Warm White"},
    "lab_lights": {"type": "light", "state": "ON", "brightness": 100, "color": "Cyan Blue"},
    "thermostat": {"type": "climate", "temperature": 22.5, "mode": "COOL"},
    "security_system": {"type": "security", "armed": True, "status": "ALL_SECURE"},
    "media_player": {"type": "media", "state": "PAUSED", "current_track": "AC/DC - Back in Black"},
}


# ==========================================================================
#  Persistent Memory (SQLite)
# ==========================================================================
def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_db()
    try:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS memory (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                category   TEXT NOT NULL,
                key        TEXT NOT NULL,
                value      TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        conn.commit()
        count = conn.execute("SELECT COUNT(*) AS c FROM memory").fetchone()["c"]
        if count == 0:
            now = datetime.datetime.now().isoformat()
            conn.executemany(
                "INSERT INTO memory (category, key, value, created_at) VALUES (?, ?, ?, ?)",
                [(c, k, v, now) for (c, k, v) in SEED_MEMORY],
            )
            conn.commit()
            logger.info(f"Seeded memory with {len(SEED_MEMORY)} initial facts.")
    finally:
        conn.close()


def add_memory(category: str, key: str, value: str) -> None:
    conn = get_db()
    try:
        conn.execute(
            "INSERT INTO memory (category, key, value, created_at) VALUES (?, ?, ?, ?)",
            (category, key, value, datetime.datetime.now().isoformat()),
        )
        conn.commit()
    finally:
        conn.close()


def set_memory(category: str, key: str, value: str) -> None:
    """Upsert: update the existing (category, key) row if present, else insert.
    Used for singleton facts like the user's name so we never duplicate them."""
    conn = get_db()
    try:
        row = conn.execute(
            "SELECT id FROM memory WHERE category = ? AND key = ?", (category, key)
        ).fetchone()
        now = datetime.datetime.now().isoformat()
        if row:
            conn.execute("UPDATE memory SET value = ?, created_at = ? WHERE id = ?", (value, now, row["id"]))
        else:
            conn.execute(
                "INSERT INTO memory (category, key, value, created_at) VALUES (?, ?, ?, ?)",
                (category, key, value, now),
            )
        conn.commit()
    finally:
        conn.close()


# Values that are placeholders, not a real name the user gave us.
_PLACEHOLDER_NAMES = {"sir / creator", "sir", "creator", "user", ""}


def get_user_name() -> str:
    """Returns the user's real name if known, else empty string."""
    conn = get_db()
    try:
        row = conn.execute(
            "SELECT value FROM memory WHERE category = 'user' AND key = 'name'"
        ).fetchone()
    finally:
        conn.close()
    if not row:
        return ""
    name = (row["value"] or "").strip()
    return "" if name.lower() in _PLACEHOLDER_NAMES else name


def all_memory() -> List[sqlite3.Row]:
    conn = get_db()
    try:
        return conn.execute("SELECT * FROM memory ORDER BY id").fetchall()
    finally:
        conn.close()


def search_memory(query: str, limit: int = 8) -> List[sqlite3.Row]:
    """Naive keyword retrieval across key/value/category. Phase 2 will upgrade to embeddings."""
    terms = [t for t in re.split(r"\W+", query.lower()) if len(t) > 2]
    rows = all_memory()
    if not terms:
        return rows[:limit]
    scored = []
    for r in rows:
        haystack = f"{r['category']} {r['key']} {r['value']}".lower()
        score = sum(1 for t in terms if t in haystack)
        if score:
            scored.append((score, r))
    scored.sort(key=lambda x: x[0], reverse=True)
    hits = [r for _, r in scored[:limit]]
    return hits if hits else rows[:limit]


def build_memory_context(query: str) -> str:
    rows = search_memory(query)
    if not rows:
        return "(No stored personal information yet.)"
    return "\n".join(f"- [{r['category']}] {r['key']}: {r['value']}" for r in rows)


# ==========================================================================
#  Deterministic actions (executed for real, then phrased by the LLM)
# ==========================================================================
def maybe_run_action(user_text: str) -> Optional[str]:
    """Detects a concrete device action, performs it, and returns a plain-English
    outcome note for the LLM to confirm naturally. Returns None if no action."""
    t = user_text.lower()
    if ("light" in t or "lights" in t) and ("on" in t or "off" in t or "turn" in t):
        action = "ON" if " on" in f" {t}" and "off" not in t else "OFF"
        device = "living_room_light" if "living" in t else "lab_lights"
        IOT_DEVICES[device]["state"] = action
        return f"Action performed: {device.replace('_', ' ')} switched {action}."
    return None


# --- Phone actions the Android app executes locally (Phase 3) --------------
CALL_RE = re.compile(r"^\s*(?:please\s+)?(?:call|phone|dial|ring)\s+(?:up\s+)?(.+)$", re.IGNORECASE)
OPEN_RE = re.compile(r"^\s*(?:please\s+)?(?:open|launch|start|go to)\s+(?:the\s+)?(.+)$", re.IGNORECASE)


def _clean_target(text: str) -> str:
    text = text.strip().rstrip("?.!")
    text = re.sub(r"\b(please|now|for me|app|application)\b", "", text, flags=re.IGNORECASE)
    return text.strip()


def detect_device_action(user_text: str) -> Optional[Dict[str, str]]:
    """Returns a directive {type, query} for the app to execute on the phone,
    or None. 'call me X' is intentionally excluded — that sets the user's name."""
    low = user_text.strip().lower()
    if low.startswith("call me") or low.startswith("call my"):
        return None

    m = CALL_RE.match(user_text)
    if m:
        target = _clean_target(m.group(1))
        if target:
            return {"type": "CALL", "query": target}

    m = OPEN_RE.match(user_text)
    if m:
        target = _clean_target(m.group(1))
        if target:
            return {"type": "OPEN_APP", "query": target}
    return None


# ==========================================================================
#  Memory teaching ("remember that ...")
# ==========================================================================
REMEMBER_RE = re.compile(
    r"^\s*(?:remember|note|keep in mind|make a note|don't forget)(?:\s+that)?[:,\-]?\s*(.+)",
    re.IGNORECASE,
)


def maybe_store_memory(user_text: str) -> Optional[str]:
    m = REMEMBER_RE.match(user_text)
    if not m:
        return None
    fact = m.group(1).strip().rstrip(".")
    if not fact:
        return None
    key = fact[:40]
    add_memory("note", key, fact)
    logger.info(f"Stored new memory: {fact}")
    return fact


# Captures explicit name statements. Kept strict (requires "name is"/"call me")
# so casual phrases like "I'm tired" don't get mistaken for a name.
NAME_RE = re.compile(
    r"\b(?:my name is|call me|you can call me|i am called|name's)\s+([A-Za-z][A-Za-z .'\-]{0,29})",
    re.IGNORECASE,
)


def maybe_store_name(user_text: str) -> Optional[str]:
    m = NAME_RE.search(user_text)
    if not m:
        return None
    # Take the first word of the captured phrase and Title-case it.
    name = m.group(1).strip().rstrip(".").split()[0].capitalize()
    if not name or name.lower() in _PLACEHOLDER_NAMES:
        return None
    set_memory("user", "name", name)
    logger.info(f"Stored user name: {name}")
    return name


# ==========================================================================
#  LLM
# ==========================================================================
JARVIS_SYSTEM_PROMPT = """You are JARVICE — an advanced, loyal personal AI assistant modeled after Iron Man's JARVIS.

STYLE RULES (follow strictly):
- Reply ONLY in natural, spoken English. Never output JSON, code, markdown, bullet lists, or key/value dumps.
- Address the user by their name when it is given below; only fall back to "Sir" if no name is known. Speak with crisp, warm sophistication.
- Keep replies concise — usually one to three sentences.
- Use the personal information provided below as if you simply know it. Never mention "the data", "the context", or "the memory block".
- If you genuinely don't know something, say so briefly and offer to help.
"""


def call_ollama(messages: List[Dict[str, str]]) -> Optional[str]:
    payload = {
        "model": DEFAULT_MODEL,
        "messages": messages,
        "stream": False,
        "options": {"temperature": 0.7},
    }
    try:
        req = urllib.request.Request(
            OLLAMA_URL,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=OLLAMA_TIMEOUT) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            return res_data.get("message", {}).get("content", "").strip() or None
    except Exception as e:
        logger.warning(f"Ollama call failed ({e}). Falling back to local phrasing.")
        return None


def clean_reply(text: str) -> str:
    """Safety net: strip any accidental code fences / raw JSON the model might emit."""
    text = text.strip()
    text = re.sub(r"^```[a-zA-Z]*\n?", "", text)
    text = re.sub(r"\n?```$", "", text).strip()
    # If the model dumped a raw JSON object/array, don't show it to the user.
    if (text.startswith("{") and text.endswith("}")) or (text.startswith("[") and text.endswith("]")):
        return "Understood, Sir. Allow me to put that plainly — how may I assist you further?"
    return text


def fallback_reply(user_text: str, address: str, stored: Optional[str], name_set: Optional[str], action_note: Optional[str]) -> str:
    """Natural-language responses used only when the LLM is unreachable. Never JSON.
    [address] is how to refer to the user (their name, or "Sir" if unknown)."""
    if name_set:
        return f"A pleasure, {name_set}. I'll remember your name."
    if stored:
        return f"Noted, {address}. I'll remember that {stored}."
    if action_note:
        return f"Right away, {address}. {action_note}"
    t = user_text.lower()
    if "my name" in t or "who am i" in t:
        return f"You are {address}." if address != "Sir" else "I don't have your name yet — what should I call you?"
    if "schedule" in t or "today" in t or "calendar" in t:
        rows = [r for r in all_memory() if r["category"] == "schedule"]
        if rows:
            items = "; ".join(r["value"] for r in rows)
            return f"On your agenda, {address}: {items}."
        return f"Your schedule is clear for now, {address}."
    if "who are you" in t or "jarvice" in t:
        return ("I am JARVICE — Just A Rather Very Intelligent Computational Entity, "
                f"operating locally on your host server to assist you, {address}.")
    return (f"My reasoning core is momentarily offline, {address}, but I'm still at your service. "
            "Could you say that again?")


def generate_reply(user_text: str, phone_context: Dict[str, Any], history: List[Dict[str, str]]):
    """Returns (reply_text, action) where action is a phone directive or None."""
    name_set = maybe_store_name(user_text)
    user_name = get_user_name()
    address = user_name if user_name else "Sir"

    # Phone actions are handled deterministically and returned immediately (no LLM
    # round-trip) so "call Mom" or "open WhatsApp" fire instantly and reliably.
    device_action = detect_device_action(user_text)
    if device_action:
        target = device_action["query"]
        if device_action["type"] == "CALL":
            text = f"Calling {target} now, {address}."
        elif device_action["type"] == "OPEN_APP":
            text = f"Opening {target}, {address}."
        else:
            text = f"Right away, {address}."
        return text, device_action

    stored = maybe_store_memory(user_text)
    action_note = maybe_run_action(user_text)
    memory_ctx = build_memory_context(user_text)
    now = datetime.datetime.now()

    identity = (
        f"The user's name is {user_name}. Address them as {user_name}."
        if user_name else
        "The user's name is unknown; address them as \"Sir\"."
    )
    context_block = (
        f"{identity}\n"
        f"Current date and time: {now:%A, %d %B %Y, %H:%M}.\n"
        f"Phone status: {json.dumps(phone_context or {})}.\n"
        f"What you know about the user:\n{memory_ctx}"
    )
    if action_note:
        context_block += f"\n\n{action_note} Confirm this to the user naturally."
    if name_set:
        context_block += f"\n\nThe user just told you their name is {name_set}. Warmly acknowledge it and use it."
    if stored:
        context_block += f"\n\nYou just saved a new fact to memory: '{stored}'. Briefly confirm you'll remember it."

    messages: List[Dict[str, str]] = [
        {"role": "system", "content": JARVIS_SYSTEM_PROMPT + "\n" + context_block}
    ]
    messages.extend(history[-MAX_HISTORY_TURNS:])
    messages.append({"role": "user", "content": user_text})

    reply = call_ollama(messages)
    if reply is None:
        reply = fallback_reply(user_text, address, stored, name_set, action_note)
    return clean_reply(reply), None


# ==========================================================================
#  HTTP + WebSocket
# ==========================================================================
@app.get("/")
def get_root():
    return {"status": "JARVICE Host Server Online", "time": datetime.datetime.now().isoformat()}


@app.websocket("/ws/jarvice")
async def websocket_jarvice_endpoint(websocket: WebSocket):
    await websocket.accept()
    logger.info("Jarvice Android Client connected over WebSocket.")

    history: List[Dict[str, str]] = []  # per-connection conversation memory

    await websocket.send_text(json.dumps({
        "sender": "JARVICE",
        "type": "GREETING",
        "text": "Greetings, Sir. JARVICE core systems online. Standing by for your instructions.",
        "timestamp": datetime.datetime.now().isoformat(),
    }))

    try:
        while True:
            raw_data = await websocket.receive_text()
            try:
                msg = json.loads(raw_data)
                user_text = msg.get("text", "")
                phone_context = msg.get("context", {})
                logger.info(f"Received query from client: '{user_text}'")

                # Run the (blocking) LLM call off the event loop so other clients aren't blocked.
                ai_response, action = await asyncio.to_thread(
                    generate_reply, user_text, phone_context, history
                )

                history.append({"role": "user", "content": user_text})
                history.append({"role": "assistant", "content": ai_response})

                await websocket.send_text(json.dumps({
                    "sender": "JARVICE",
                    "type": "ACTION" if action else "RESPONSE",
                    "text": ai_response,
                    "action": action,          # {"type": "CALL"|"OPEN_APP", "query": "..."} or null
                    "iot_status": IOT_DEVICES,
                    "timestamp": datetime.datetime.now().isoformat(),
                }))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({
                    "sender": "JARVICE",
                    "type": "ERROR",
                    "text": "Invalid payload format received, Sir.",
                }))
    except WebSocketDisconnect:
        logger.info("Jarvice Android Client disconnected.")


# Initialise persistent memory at import time (works under uvicorn reload too).
init_db()


if __name__ == "__main__":
    import uvicorn
    # reload disabled: the file-watch reloader spawns child processes that made
    # restarts non-deterministic. Restart the process manually after code changes.
    uvicorn.run(app, host="0.0.0.0", port=8000)
