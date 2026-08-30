import asyncio
import json
import logging
import datetime
import os
import re
import smtplib
import sqlite3
import ssl
import urllib.request
import urllib.error
import uuid as uuid_lib
from email.message import EmailMessage
from typing import Dict, Any, List, Optional, Tuple
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, status
from rag_engine import rag_engine, query_personal_documents
import pc_controller
import web_search




logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("MyJarvisServer")

app = FastAPI(title="MyJarvis Host Server", version="2.0.0")

# --- Configuration ---
OLLAMA_URL = "http://localhost:11434/api/chat"
DEFAULT_MODEL = "gemma4-e4b"          # Local Ollama model
OLLAMA_TIMEOUT = 120                   # seconds — generous so the real model always answers
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "jarvis.db")

MAX_HISTORY_TURNS = 8                  # how many past messages to keep in context per session


# --- Outgoing email -------------------------------------------------------
# Credentials come from the environment (or a gitignored .env beside this file);
# they are never hardcoded and never sent to the phone.
def _load_dotenv() -> None:
    env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.exists(env_path):
        return
    with open(env_path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_dotenv()

SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASSWORD = os.environ.get("SMTP_PASSWORD", "")
SMTP_FROM = os.environ.get("SMTP_FROM", SMTP_USER)
JARVICE_API_TOKEN = os.environ.get("JARVICE_API_TOKEN", "").strip()
MAX_MESSAGE_CHARS = 4_000

# Drafts awaiting the user's explicit approval, keyed by draft id. Nothing is ever
# sent from here without an APPROVE_EMAIL message arriving for that exact id.
PENDING_EMAILS: Dict[str, Dict[str, str]] = {}

EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")

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
    conn = sqlite3.connect(DB_PATH, timeout=10)
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
        conn.execute("CREATE INDEX IF NOT EXISTS idx_memory_category_key ON memory(category, key)")
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


# --- Phone actions the Android app executes locally --------------
CALL_RE = re.compile(r"\b(?:call|phone|dial|ring)\s+(?:up\s+)?(.+)$", re.IGNORECASE)
OPEN_RE = re.compile(r"\b(?:open|launch|start|opening|go to)\s+(?:the\s+)?(.+)$", re.IGNORECASE)

# Navigation is checked before OPEN_RE because "open maps and route to X" starts with
# "open" but is a navigation request, not a request to launch an app.
NAVIGATE_RE = re.compile(
    r"\b(?:navigate|directions?|route|take me|drive me|guide me)\b[^.]*?\bto\s+(.+)$",
    re.IGNORECASE,
)

# Trailing origin phrases Maps infers on its own — "from my current location" etc.
ORIGIN_TAIL_RE = re.compile(
    r"\s*\bfrom\s+(?:my\s+)?(?:the\s+)?(?:current\s+location|here|my\s+place|where\s+i\s+am)\b.*$",
    re.IGNORECASE,
)


def _clean_destination(text: str) -> str:
    text = ORIGIN_TAIL_RE.sub("", text)
    text = text.strip().rstrip("?.!,")
    text = re.sub(r"^\b(?:the\s+)?(?:city\s+of\s+)?", "", text, flags=re.IGNORECASE)
    return text.strip()


def _clean_target(text: str) -> str:
    text = text.strip().rstrip("?.!")
    text = re.sub(r"\b(please|now|for me|app|application|the)\b", "", text, flags=re.IGNORECASE)
    return text.strip()


FLASHLIGHT_RE = re.compile(r"\b(?:flashlight|torch|flash)\b", re.IGNORECASE)
ALARM_RE = re.compile(r"\b(?:alarm|wake me up)\b", re.IGNORECASE)
WHATSAPP_RE = re.compile(r"\b(?:whatsapp|whatsapp message)\b", re.IGNORECASE)


def detect_device_action(user_text: str) -> Optional[Dict[str, str]]:
    """Returns a directive {type, query} for the app to execute on the phone,
    or None. 'call me X' is intentionally excluded — that sets the user's name."""
    low = user_text.strip().lower()
    low = re.sub(r"^(?:hey\s+)?(?:jarvis|jarvice)[,:\s]*", "", low).strip()

    if low.startswith("call me") or low.startswith("call my"):
        return None

    # Camera intent
    if any(k in low for k in ["open camera", "opening camera", "launch camera", "take a picture", "take a photo", "open the camera", "camera app"]):
        return {"type": "OPEN_APP", "query": "camera"}

    # Maps intent
    if any(k in low for k in ["open maps", "opening maps", "open google maps", "launch maps", "show maps", "open the maps", "maps app"]):
        return {"type": "OPEN_APP", "query": "maps"}

    if FLASHLIGHT_RE.search(low):
        action_state = "OFF" if "off" in low else "ON"
        return {"type": "FLASHLIGHT", "query": action_state}

    if ALARM_RE.search(low):
        return {"type": "SET_ALARM", "query": user_text}

    if WHATSAPP_RE.search(low):
        msg_content = user_text
        if "saying" in low:
            msg_content = user_text.split("saying", 1)[1].strip()
        return {"type": "WHATSAPP", "query": msg_content}

    m = NAVIGATE_RE.search(low)
    if m:
        destination = _clean_destination(m.group(1))
        if destination:
            return {"type": "NAVIGATE", "query": destination}

    m = CALL_RE.search(low)
    if m:
        target = _clean_target(m.group(1))
        if target:
            return {"type": "CALL", "query": target}

    m = OPEN_RE.search(low)
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
JARVIS_SYSTEM_PROMPT = """You are JARVIS — an advanced, loyal personal AI assistant modeled after Iron Man's JARVIS.

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
    if "who are you" in t or "jarvis" in t or "jarvice" in t:
        return ("I am JARVIS — Just A Rather Very Intelligent System, "
                f"operating locally on your host server to assist you, {address}.")
    return (f"My reasoning core is momentarily offline, {address}, but I'm still at your service. "
            "Could you say that again?")


# ==========================================================================
#  Email connector (draft -> user approval -> send)
# ==========================================================================
def smtp_configured() -> bool:
    return bool(SMTP_USER and SMTP_PASSWORD)


def lookup_contact_email(name: str) -> Optional[str]:
    """Finds a saved address for a person, e.g. 'alex' -> alex@example.com."""
    needle = name.strip().lower()
    if not needle:
        return None
    for row in all_memory():
        if row["category"] != "contact_email":
            continue
        if row["key"].strip().lower() == needle:
            return row["value"]
    return None


def maybe_store_contact_email(user_text: str) -> Optional[str]:
    """Handles 'Alex's email is alex@example.com' so future sends can use the name."""
    match = re.search(
        r"([A-Za-z][\w .'-]{0,40}?)(?:'s|s')?\s+(?:e-?mail|email address)\s+(?:is|=)\s+([\w.+-]+@[\w-]+\.[\w.-]+)",
        user_text,
        re.IGNORECASE,
    )
    if not match:
        return None
    name = match.group(1).strip().strip(".,")
    address = match.group(2).strip()
    for filler in ("remember that", "remember", "note that", "save that", "please"):
        if name.lower().startswith(filler):
            name = name[len(filler):].strip()
    if not name:
        return None
    conn = get_db()
    try:
        conn.execute(
            "INSERT INTO memory (category, key, value, created_at) VALUES (?, ?, ?, ?)",
            ("contact_email", name.lower(), address, datetime.datetime.now().isoformat()),
        )
        conn.commit()
    finally:
        conn.close()
    logger.info(f"Stored contact email: {name} -> {address}")
    return f"{name} ({address})"


def detect_email_intent(user_text: str) -> bool:
    t = user_text.lower()
    if not re.search(r"\b(e-?mail|mail)\b", t):
        return False
    return bool(re.search(r"\b(send|write|draft|compose|shoot|fire off|email)\b", t))


def resolve_recipient(user_text: str) -> Optional[str]:
    """Explicit address in the utterance wins; otherwise try the saved contacts."""
    explicit = EMAIL_RE.search(user_text)
    if explicit:
        return explicit.group(0)

    match = re.search(
        r"\b(?:e-?mail|mail|message)\s+(?:to\s+)?([A-Za-z][\w .'-]{0,40}?)(?:\s+(?:that|about|saying|and|to)\b|[,.]|$)",
        user_text,
        re.IGNORECASE,
    )
    if match:
        return lookup_contact_email(match.group(1))
    return None


EMAIL_DRAFT_PROMPT = """You write short, professional emails on the user's behalf.
Return ONLY a JSON object with exactly these keys: "subject", "body".
No markdown, no code fences, no commentary.
The body must be plain text, ready to send, signed off with the user's name.
Keep it brief — three sentences at most unless the request demands more.
Never leave placeholders such as [Time], [Name] or TBD; if a detail is unknown,
write around it so the email reads naturally as-is."""


def draft_email_content(user_text: str, sender_name: str) -> Dict[str, str]:
    """Asks the LLM for a subject/body pair, with a deterministic fallback."""
    messages = [
        {"role": "system", "content": EMAIL_DRAFT_PROMPT},
        {
            "role": "user",
            "content": (
                f"The sender's name is {sender_name}.\n"
                f"Write the email for this request: {user_text}"
            ),
        },
    ]
    raw = call_ollama(messages)
    if raw:
        cleaned = re.sub(r"^```[a-zA-Z]*\n?", "", raw.strip())
        cleaned = re.sub(r"\n?```$", "", cleaned).strip()
        try:
            parsed = json.loads(cleaned)
            subject = str(parsed.get("subject", "")).strip()
            body = str(parsed.get("body", "")).strip()
            if subject and body:
                return {"subject": subject, "body": body}
        except (json.JSONDecodeError, AttributeError):
            logger.warning("Email draft was not valid JSON; using fallback wording.")

    return {
        "subject": "A quick note",
        "body": f"Hello,\n\n{user_text.strip()}\n\nBest regards,\n{sender_name}",
    }


def send_email_smtp(to_address: str, subject: str, body: str) -> None:
    """Blocking SMTP send. Raises on failure so the caller can report it."""
    message = EmailMessage()
    message["From"] = SMTP_FROM
    message["To"] = to_address
    message["Subject"] = subject
    message.set_content(body)

    context = ssl.create_default_context()
    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=30) as server:
        server.ehlo()
        server.starttls(context=context)
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.send_message(message)


def build_email_draft(user_text: str, address: str) -> tuple:
    """Returns (reply_text, pending_email_or_None) for an email request."""
    if not smtp_configured():
        return (
            f"I can draft it, {address}, but my mail credentials aren't configured yet. "
            "Set SMTP_USER and SMTP_PASSWORD on the host server and I'll be able to send.",
            None,
        )

    recipient = resolve_recipient(user_text)
    if not recipient:
        return (
            f"Certainly, {address} — who should I send it to? "
            "Give me the address, or tell me their email once and I'll remember it.",
            None,
        )

    draft = draft_email_content(user_text, address)
    draft_id = uuid_lib.uuid4().hex
    pending = {
        "id": draft_id,
        "to": recipient,
        "subject": draft["subject"],
        "body": draft["body"],
    }
    PENDING_EMAILS[draft_id] = pending

    return (
        f"I've drafted an email to {recipient}, {address}. "
        "Review it and approve when you're happy.",
        pending,
    )


def detect_pc_action(user_text: str) -> Optional[Tuple[str, Optional[str]]]:
    """
    Detects if user requested a Host PC action.
    Returns (reply_text, base64_image_or_None) if handled, or None if not a PC action.
    """
    t = user_text.lower().strip()

    # 1. Desktop Screenshot
    if any(k in t for k in ["screenshot", "screen shot", "capture screen", "capture desktop", "show my pc screen", "show desktop", "host screen", "pc display", "pc screen", "take a screenshot", "take screenshot"]):
        b64 = pc_controller.capture_desktop_screenshot()
        if b64:
            return "Capturing current display of your host workstation now, Sir.", b64
        return "I attempted to capture the host display, Sir, but the display buffer was momentarily inaccessible.", None

    # 2. System Telemetry / Resource Monitor
    if any(k in t for k in ["cpu", "ram", "memory", "specs", "system stats", "telemetry", "hardware", "pc status", "disk space", "storage", "pc health", "workstation status"]) and any(k in t for k in ["pc", "system", "computer", "host", "stats", "load", "drive", "drives", "health"]):
        stats = pc_controller.get_system_telemetry()
        narrative = pc_controller.format_telemetry_narrative(stats)
        return narrative, None

    # 3. Lock Workstation
    if "lock" in t and any(k in t for k in ["pc", "computer", "workstation", "screen", "system", "desktop"]):
        res = pc_controller.lock_workstation()
        return res, None

    # 4. Volume / Mute
    if "mute" in t and any(k in t for k in ["pc", "computer", "audio", "sound", "host", "speakers"]):
        res = pc_controller.toggle_mute()
        return res, None

    if "volume" in t and any(k in t for k in ["pc", "computer", "host", "speakers"]):
        nums = re.findall(r"\b\d+\b", t)
        if nums:
            target_vol = int(nums[0])
            res = pc_controller.set_master_volume(target_vol)
            return res, None
        if "up" in t or "increase" in t or "raise" in t:
            res = pc_controller.set_master_volume(75)
            return res, None
        if "down" in t or "lower" in t or "decrease" in t:
            res = pc_controller.set_master_volume(25)
            return res, None

    # 5. Media Player Controls
    if any(k in t for k in ["play", "pause", "resume", "next track", "previous track", "stop music", "skip song", "next song"]) and any(k in t for k in ["pc", "spotify", "media", "music", "song", "track"]):
        if "next" in t or "skip" in t:
            res = pc_controller.control_media("next")
        elif "prev" in t or "back" in t:
            res = pc_controller.control_media("prev")
        elif "stop" in t:
            res = pc_controller.control_media("stop")
        else:
            res = pc_controller.control_media("playpause")
        return res, None

    # 6. Launch PC App / PC Camera
    is_pc_specified = any(target in t for target in ["on pc", "on my pc", "on computer", "on my computer", "on laptop", "on host", "on workstation", "pc camera", "pc chrome", "pc terminal", "pc vscode", "pc notepad", "pc spotify"])
    if is_pc_specified or any(t.startswith(prefix) for prefix in ["open on pc", "launch on pc", "start on pc", "open on my pc", "launch on my pc", "run on pc", "launch pc"]):
        if "camera" in t or "webcam" in t:
            res = pc_controller.launch_pc_application("camera")
            return res, None
        app_query = re.sub(r"\b(open|launch|start|run|on|my|pc|computer|laptop|host|workstation|the|app|program)\b", "", t).strip()
        if app_query:
            res = pc_controller.launch_pc_application(app_query)
            return res, None

    return None



def generate_reply(user_text: str, phone_context: Dict[str, Any], history: List[Dict[str, str]]) -> Tuple[str, Optional[Dict[str, Any]], Optional[Dict[str, Any]], Optional[str], List[Dict[str, str]]]:
    """Returns (reply_text, action, pending_email, image_payload, web_sources); action/pending_email/image_payload may be None."""
    name_set = maybe_store_name(user_text)
    user_name = get_user_name()
    address = user_name if user_name else "Sir"
    web_sources: List[Dict[str, str]] = []

    # Host PC Remote Automation check
    pc_res = detect_pc_action(user_text)
    if pc_res:
        reply_txt, img_b64 = pc_res
        return reply_txt, None, None, img_b64, []

    # "Alex's email is ..." — save it before anything else so the same sentence can
    # also be used to address a message.
    saved_contact = maybe_store_contact_email(user_text)
    if saved_contact and not detect_email_intent(user_text):
        return f"Noted, {address}. I'll remember {saved_contact}.", None, None, None, []

    if detect_email_intent(user_text):
        reply_text, pending = build_email_draft(user_text, address)
        return reply_text, None, pending, None, []

    # Phone actions are handled deterministically and returned immediately (no LLM
    # round-trip) so "call Mom" or "open WhatsApp" fire instantly and reliably.
    device_action = detect_device_action(user_text)
    if device_action:
        target = device_action["query"]
        if device_action["type"] == "CALL":
            text = f"Calling {target} now, {address}."
        elif device_action["type"] == "OPEN_APP":
            text = f"Opening {target}, {address}."
        elif device_action["type"] == "NAVIGATE":
            text = f"Starting navigation to {target}, {address}."
        else:
            text = f"Right away, {address}."
        return text, device_action, None, None, []

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

    low_text = user_text.lower().strip()

    # 1. On-demand RAG codebase & document re-indexing
    if any(k in low_text for k in ["reindex", "re-index", "index pc", "refresh index", "index my files", "index codebase", "scan my files", "rescan pc"]):
        index_res = rag_engine.index_all()
        files_cnt = index_res.get("total_files", rag_engine.total_indexed_files)
        chunks_cnt = index_res.get("total_chunks", rag_engine.total_indexed_chunks)
        dur = index_res.get("duration_secs", 0.1)
        return f"Host PC indexing completed in {dur} seconds, {address}. {files_cnt} local project and document files ({chunks_cnt} code segments) are indexed for semantic retrieval.", None, None, None, []

    # 2. Codebase & Document Semantic RAG Retrieval
    if any(k in low_text for k in ["code", "function", "file", "files", "project", "doc", "docs", "document", "pdf", "notes", "protocol", "search pc", "read file", "where is", "how is", "implementation", "class", "method", "variable", "folder", "module", "android"]):
        doc_context = query_personal_documents(user_text)
        if doc_context:
            context_block += (
                f"\n\n[Retrieved Excerpts from User's Local PC Codebase & Documents]:\n"
                f"{doc_context}\n\n"
                "INSTRUCTION: Use the above local file excerpts to directly and accurately answer the user's question. "
                "Explicitly mention the relevant file names and line numbers where appropriate."
            )

    # 3. Live Web Search & Real-Time Knowledge Grounding
    is_web_query = any(k in low_text for k in [
        "weather", "temperature", "forecast", "rain", "humidity", "climate",
        "news", "headline", "headlines", "latest on", "breaking news",
        "bitcoin", "btc", "ethereum", "crypto", "price of", "stock price",
        "search the web", "search online", "look up", "google", "who is",
        "who was", "what is", "tell me about", "history of", "latest"
    ]) and not any(k in low_text for k in ["my pc", "on pc", "in my code", "my project", "screenshot", "lock pc", "camera on pc"])

    if is_web_query:
        web_res = web_search.search_web(user_text)
        if web_res and web_res.evidence_text:
            web_sources = web_res.sources
            context_block += (
                f"\n\n[Live Real-Time Web Evidence]:\n"
                f"{web_res.evidence_text}\n\n"
                "INSTRUCTION: Use the above real-time live web evidence to answer the user's question accurately with up-to-date facts."
            )

    messages: List[Dict[str, str]] = [
        {"role": "system", "content": JARVIS_SYSTEM_PROMPT + "\n" + context_block}
    ]
    messages.extend(history[-MAX_HISTORY_TURNS:])
    messages.append({"role": "user", "content": user_text})

    reply = call_ollama(messages)
    if reply is None:
        reply = fallback_reply(user_text, address, stored, name_set, action_note)
    return clean_reply(reply), None, None, None, web_sources





def handle_email_verdict(verdict: Dict[str, Any]) -> str:
    """Sends (or discards) a draft. A draft is consumed either way, so an approval
    can never be replayed to send the same mail twice."""
    draft_id = str(verdict.get("id", ""))
    approved = bool(verdict.get("approved"))

    pending = PENDING_EMAILS.pop(draft_id, None)
    if not pending:
        return "That draft has already been dealt with, Sir."

    if not approved:
        logger.info(f"Email draft {draft_id} discarded by user.")
        return "Discarded, Sir. Nothing was sent."

    try:
        send_email_smtp(pending["to"], pending["subject"], pending["body"])
    except Exception as exc:
        logger.error(f"Failed to send email: {exc}")
        return f"I couldn't send it, Sir — the mail server refused: {exc}"

    logger.info(f"Email sent to {pending['to']}")
    return f"Sent to {pending['to']}, Sir."


# ==========================================================================
#  HTTP + WebSocket
# ==========================================================================
@app.get("/")
def get_root():
    return {"status": "JARVIS Host Server Online", "time": datetime.datetime.now().isoformat()}


@app.websocket("/ws/jarvis")
@app.websocket("/ws/jarvice")
async def websocket_jarvis_endpoint(websocket: WebSocket):
    token = os.environ.get("JARVIS_API_TOKEN", os.environ.get("JARVICE_API_TOKEN", "jarvis_local_token")).strip()
    auth = websocket.headers.get("authorization", "")

    if token and auth and auth != f"Bearer {token}":
        logger.warning(f"Rejected WebSocket connection with invalid token: {auth}")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid pairing token")
        return

    await websocket.accept()
    logger.info("✅ Jarvis Android Client connected successfully over WebSocket.")



    history: List[Dict[str, str]] = []  # per-connection conversation memory

    await websocket.send_text(json.dumps({
        "sender": "JARVIS",
        "type": "GREETING",
        "text": "Greetings, Sir. JARVIS core systems online. Standing by for your instructions.",
        "timestamp": datetime.datetime.now().isoformat(),
    }))

    try:
        while True:
            raw_data = await websocket.receive_text()
            try:
                msg = json.loads(raw_data)

                # --- Approval verdict for a previously drafted email ---
                verdict = msg.get("approve_email")
                if verdict:
                    reply_text = await asyncio.to_thread(handle_email_verdict, verdict)
                    await websocket.send_text(json.dumps({
                        "sender": "JARVIS",
                        "type": "RESPONSE",
                        "text": reply_text,
                        "timestamp": datetime.datetime.now().isoformat(),
                    }))
                    continue

                user_text = msg.get("query") or msg.get("text") or ""
                phone_context = msg.get("device_context") or msg.get("context") or {}
                if not isinstance(user_text, str) or not user_text.strip() or len(user_text) > MAX_MESSAGE_CHARS:
                    await websocket.send_text(json.dumps({"sender": "JARVIS", "type": "ERROR", "text": "Please send a non-empty message up to 4,000 characters."}))
                    continue
                if not isinstance(phone_context, dict):
                    phone_context = {}
                logger.info("Received query from authenticated client: '%s' (%d characters).", user_text[:60], len(user_text))


                # Run the (blocking) LLM call off the event loop so other clients aren't blocked.
                ai_response, action, pending_email, image_payload, web_sources = await asyncio.to_thread(
                    generate_reply, user_text, phone_context, history
                )

                history.append({"role": "user", "content": user_text})
                history.append({"role": "assistant", "content": ai_response})

                await websocket.send_text(json.dumps({
                    "sender": "JARVIS",
                    "type": "ACTION" if action else "RESPONSE",
                    "text": ai_response,
                    "action": action,          # {"type": "CALL"|"OPEN_APP", "query": "..."} or null
                    "pending_email": pending_email,   # draft awaiting approval, or null
                    "image": image_payload,           # Base64 desktop screenshot or null
                    "web_sources": web_sources,       # List of {"title": "...", "url": "...", "domain": "..."}
                    "iot_status": IOT_DEVICES,
                    "timestamp": datetime.datetime.now().isoformat(),
                }))


            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({
                    "sender": "JARVIS",
                    "type": "ERROR",
                    "text": "Invalid payload format received, Sir.",
                }))
    except WebSocketDisconnect:
        logger.info("Jarvice Android Client disconnected.")


# Initialise persistent memory and start background RAG codebase indexing
init_db()
rag_engine.start_background_indexing()



if __name__ == "__main__":
    import uvicorn
    # reload disabled: the file-watch reloader spawns child processes that made
    # restarts non-deterministic. Restart the process manually after code changes.
    certfile = os.environ.get("JARVICE_TLS_CERT", "").strip()
    keyfile = os.environ.get("JARVICE_TLS_KEY", "").strip()
    if bool(certfile) != bool(keyfile):
        raise RuntimeError("Set both JARVICE_TLS_CERT and JARVICE_TLS_KEY, or neither.")
    uvicorn.run(
        app,
        host=os.environ.get("JARVICE_HOST", "127.0.0.1"),
        port=int(os.environ.get("JARVICE_PORT", "8000")),
        ssl_certfile=certfile or None,
        ssl_keyfile=keyfile or None,
    )
