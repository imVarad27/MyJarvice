"""Native Ollama phone-tool schemas and deterministic result validation.

The model understands the user's language and chooses a function. This module
contains no phrase matcher and never executes arbitrary model output.
"""
from dataclasses import dataclass
import json
from typing import Any, Dict, Optional


@dataclass(frozen=True)
class ToolSpec:
    description: str
    argument: Optional[str] = None
    argument_description: str = ""
    allowed_values: tuple[str, ...] = ()
    max_length: int = 0


PHONE_TOOLS: Dict[str, ToolSpec] = {
    "phone_status": ToolSpec("Read the phone's current battery, charging state, network and local time."),
    "open_phone_app": ToolSpec("Open an installed app on the Android phone.", "app", "Installed app name.", max_length=80),
    "navigate_phone": ToolSpec("Open turn-by-turn directions on the Android phone.", "destination", "Destination requested by the user.", max_length=160),
    "flashlight": ToolSpec("Change the Android phone flashlight.", "state", "Requested flashlight state.", ("on", "off", "toggle"), 6),
    "set_phone_alarm": ToolSpec("Prepare an alarm in the Android clock app.", "when", "Time expression supplied by the user.", max_length=80),
    "set_phone_timer": ToolSpec("Prepare a timer in the Android clock app.", "duration", "Duration supplied by the user.", max_length=80),
    "call_phone": ToolSpec("Prepare a phone call. The Android app will require explicit confirmation.", "target", "Contact name or phone number.", max_length=120),
    "whatsapp_phone": ToolSpec("Prepare a WhatsApp message. The Android app will require explicit confirmation.", "message", "Message text to prepare.", max_length=500),
}


@dataclass(frozen=True)
class ToolDecision:
    name: str
    argument: str


def ollama_tools() -> list[dict[str, Any]]:
    tools = []
    for name, spec in PHONE_TOOLS.items():
        properties: Dict[str, Any] = {}
        required: list[str] = []
        if spec.argument is not None:
            field: Dict[str, Any] = {"type": "string", "description": spec.argument_description}
            if spec.allowed_values:
                field["enum"] = list(spec.allowed_values)
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
    if name not in PHONE_TOOLS or not isinstance(arguments, dict):
        return None
    spec = PHONE_TOOLS[name]
    if spec.argument is None:
        if arguments:
            return None
        return ToolDecision(name, "")
    if set(arguments) != {spec.argument} or not isinstance(arguments.get(spec.argument), str):
        return None
    argument = arguments[spec.argument].strip()
    if not argument or len(argument) > spec.max_length:
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
