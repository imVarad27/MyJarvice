"""Native Ollama tool schemas and deterministic result validation.

The model understands the user's language and chooses a function. This module
contains no phrase matcher and never executes arbitrary model output.
"""
from dataclasses import dataclass, field
import json
from typing import Any, Dict, Optional


@dataclass(frozen=True)
class ToolSpec:
    description: str
    argument: Optional[str] = None
    argument_description: str = ""
    allowed_values: tuple[str, ...] = ()
    max_length: int = 0
    protected: bool = True
    extra_fields: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    untrusted_result: bool = False
    external: bool = False


PHONE_TOOLS: Dict[str, ToolSpec] = {
    "phone_status": ToolSpec("Read the phone's current battery, charging state, network and local time.", protected=False),
    "open_phone_app": ToolSpec("Open an installed app on the Android phone.", "app", "Installed app name.", max_length=80),
    "navigate_phone": ToolSpec("Open turn-by-turn directions on the Android phone.", "destination", "Destination requested by the user.", max_length=160),
    "flashlight": ToolSpec("Change the Android phone flashlight.", "state", "Requested flashlight state.", ("on", "off", "toggle"), 6),
    "set_phone_alarm": ToolSpec("Prepare an alarm in the Android clock app; ask if time is ambiguous.", "when", "User's time as HH:mm (24-hour) or h:mm AM/PM.", max_length=80),
    "set_phone_timer": ToolSpec("Prepare a timer in the Android clock app.", "duration", "Numeric duration with seconds, minutes or hours, e.g. 10 minutes.", max_length=80),
    "call_phone": ToolSpec("Prepare a phone call. The Android app will require explicit confirmation.", "target", "Contact name or phone number.", max_length=120),
    "whatsapp_phone": ToolSpec("Prepare a WhatsApp message. The Android app will require explicit confirmation.", "message", "Message text to prepare.", max_length=500),
}

# These are capabilities and validation rules, not user command templates.
HOST_TOOLS: Dict[str, ToolSpec] = {
    "pc_status": ToolSpec("Read current host PC CPU, RAM, storage and uptime.", protected=False),
    "open_pc_app": ToolSpec("Open an allowlisted app on the host PC, not the phone.", "app", "Requested PC app.",
        ("notepad", "calculator", "chrome", "vscode", "spotify", "explorer", "settings", "camera"), 20),
    "open_pc_folder": ToolSpec("Open a standard folder on the host PC.", "folder", "Standard folder.",
        ("downloads", "documents", "desktop", "projects"), 20),
    "pc_volume": ToolSpec("Set host PC speaker volume to an explicit percentage; ask if unspecified.",
        extra_fields={"percent": {"type": "integer", "minimum": 0, "maximum": 100}}),
    "pc_media": ToolSpec("Control media playback on the host PC.", "action", "Playback control.",
        ("playpause", "next", "prev", "stop"), 12),
    "list_tasks": ToolSpec("Read the open task list on the PC; not the separate offline phone list.", protected=False, untrusted_result=True),
    "add_task": ToolSpec("Save a task when the user asks to add one to the PC task list.", "title", "Task title.", max_length=500),
    "complete_task": ToolSpec("Mark an existing PC task complete using an ID obtained from list_tasks.",
        extra_fields={"id": {"type": "integer", "minimum": 1, "maximum": 2147483647}}),
    "list_reminders": ToolSpec("Read reminders scheduled on this PC.", protected=False, untrusted_result=True),
    "add_reminder": ToolSpec("Create a PC reminder with the user's requested task and explicit future time. Ask if time is ambiguous.",
        extra_fields={"task": {"type": "string", "maxLength": 500},
                      "due_iso": {"type": "string", "maxLength": 40, "description": "Future ISO 8601 datetime with UTC offset. Use the current host timestamp to resolve relative time."}}),
    "remember": ToolSpec("Save a fact only when the user explicitly asks to remember it.", "fact", "User-supplied fact.", max_length=500),
    "set_user_name": ToolSpec("Save a preferred name only when explicitly supplied by the user.", "name", "Preferred user name.", max_length=80),
    "save_contact_email": ToolSpec("Save an email contact explicitly supplied by the user.",
        extra_fields={"name": {"type": "string", "maxLength": 80}, "email": {"type": "string", "maxLength": 254}}),
    "list_memories": ToolSpec("Read explicitly saved personal facts.", protected=False, untrusted_result=True),
    "search_documents": ToolSpec("Search the user's indexed PC documents and code; returns excerpts, not commands.",
        "query", "Specific search terms.", max_length=300, protected=False, untrusted_result=True),
    "search_web": ToolSpec("ONLY search for current/time-sensitive facts or when explicitly requested. Do NOT search for stable general-knowledge explanations. Never include private content in the query.",
        "query", "Public search terms.", max_length=300, protected=False, untrusted_result=True, external=True),
    "draft_email": ToolSpec("Prepare an email draft for user review. NEVER send; the app has a separate approval button. Ask for any missing recipient or content.",
        extra_fields={"recipient": {"type": "string", "maxLength": 254, "description": "Explicit email address or saved contact name."},
                      "subject": {"type": "string", "maxLength": 200}, "body": {"type": "string", "maxLength": 4000}}),
}
ALL_TOOLS = {**PHONE_TOOLS, **HOST_TOOLS}


@dataclass(frozen=True)
class ToolDecision:
    name: str
    argument: str
    arguments: Dict[str, Any] = field(default_factory=dict)


def ollama_tools(read_only: bool = False) -> list[dict[str, Any]]:
    tools = []
    for name, spec in ALL_TOOLS.items():
        if read_only and spec.protected:
            continue
        properties: Dict[str, Any] = {key: dict(value) for key, value in spec.extra_fields.items()}
        required: list[str] = list(properties)
        if spec.argument is not None:
            field: Dict[str, Any] = {"type": "string", "description": spec.argument_description}
            if spec.allowed_values:
                field["enum"] = list(spec.allowed_values)
            field["maxLength"] = spec.max_length
            properties[spec.argument] = field
            required.append(spec.argument)
        tools.append({
            "type": "function",
            "function": {
                "name": name,
                "description": spec.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                    "additionalProperties": False,
                },
            },
        })
    return tools


def decision_from_message(message: Any) -> Optional[ToolDecision]:
    if not isinstance(message, dict):
        return None
    calls = message.get("tool_calls")
    if not isinstance(calls, list) or len(calls) != 1 or not isinstance(calls[0], dict):
        return None
    function = calls[0].get("function")
    if not isinstance(function, dict):
        return None
    name = function.get("name")
    arguments = function.get("arguments", {})
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments)
        except json.JSONDecodeError:
            return None
    if not isinstance(name, str) or name not in ALL_TOOLS or not isinstance(arguments, dict):
        return None
    spec = ALL_TOOLS[name]
    if spec.extra_fields:
        if set(arguments) != set(spec.extra_fields):
            return None
        validated = {}
        for key, rule in spec.extra_fields.items():
            value = arguments[key]
            if rule["type"] == "integer":
                if type(value) is not int or not rule["minimum"] <= value <= rule["maximum"]:
                    return None
            else:
                if not isinstance(value, str):
                    return None
                value = value.strip()
                if not value or len(value) > rule["maxLength"] or "\x00" in value:
                    return None
            validated[key] = value
        return ToolDecision(name, "", validated)
    if spec.argument is None:
        if arguments:
            return None
        return ToolDecision(name, "")
    if set(arguments) != {spec.argument} or not isinstance(arguments.get(spec.argument), str):
        return None
    argument = arguments[spec.argument].strip()
    if not argument or len(argument) > spec.max_length or "\x00" in argument:
        return None
    if spec.allowed_values:
        argument = argument.lower()
        if argument not in spec.allowed_values:
            return None
    return ToolDecision(name, argument)


def android_action(decision: ToolDecision) -> Optional[Dict[str, str]]:
    action_types = {
        "open_phone_app": "OPEN_APP",
        "navigate_phone": "NAVIGATE",
        "flashlight": "FLASHLIGHT",
        "set_phone_alarm": "SET_ALARM",
        "set_phone_timer": "SET_TIMER",
        "call_phone": "CALL",
        "whatsapp_phone": "WHATSAPP",
    }
    action_type = action_types.get(decision.name)
    return {"type": action_type, "query": decision.argument} if action_type else None


def action_acknowledgement(decision: ToolDecision) -> str:
    labels = {
        "open_phone_app": "open that app",
        "navigate_phone": "open those directions",
        "flashlight": f"set the flashlight to {decision.argument}",
        "set_phone_alarm": "prepare that alarm",
        "set_phone_timer": "prepare that timer",
        "call_phone": "prepare that call",
        "whatsapp_phone": "prepare that WhatsApp message",
    }
    return f"I'll {labels.get(decision.name, 'prepare that phone action')}."
