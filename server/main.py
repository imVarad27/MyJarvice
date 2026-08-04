import asyncio
import json
import logging
import datetime
import urllib.request
import urllib.error
from typing import Dict, Any, List
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("MyJarviceServer")

app = FastAPI(title="MyJarvice Host Server", version="1.0.0")

# --- Configuration ---
OLLAMA_URL = "http://localhost:11434/api/chat"
DEFAULT_MODEL = "gemma4-e4b"  # Google Gemma 4 E4B (Hugging Face GGUF Model)

# --- In-Memory Personal Vector/Knowledge Store Mock ---
PERSONAL_KNOWLEDGE_BASE = [
    {"category": "user", "key": "name", "value": "Sir / Creator"},
    {"category": "preference", "key": "coffee", "value": "Prefers espresso with light oat milk"},
    {"category": "schedule", "key": "daily_standup", "value": "Daily team standup at 10:00 AM"},
    {"category": "schedule", "key": "gym", "value": "Gym workout scheduled at 6:30 PM"},
    {"category": "contact", "key": "emergency", "value": "Primary emergency contact is Alex"},
    {"category": "note", "key": "project", "value": "MyJarvice project phase 1 local deployment in progress"}
]

# --- In-Memory IoT Device State ---
IOT_DEVICES = {
    "living_room_light": {"type": "light", "state": "OFF", "brightness": 80, "color": "Warm White"},
    "lab_lights": {"type": "light", "state": "ON", "brightness": 100, "color": "Cyan Blue"},
    "thermostat": {"type": "climate", "temperature": 22.5, "mode": "COOL"},
    "security_system": {"type": "security", "armed": True, "status": "ALL_SECURE"},
    "media_player": {"type": "media", "state": "PAUSED", "current_track": "AC/DC - Back in Black"}
}

# --- Tool Execution Registry ---
def execute_tool(tool_name: str, arguments: Dict[str, Any]) -> str:
    logger.info(f"Executing tool '{tool_name}' with args: {arguments}")
    
    if tool_name == "get_personal_schedule":
        items = [k for k in PERSONAL_KNOWLEDGE_BASE if k["category"] == "schedule"]
        return f"Schedule retrieved: {json.dumps(items)}"
    
    elif tool_name == "search_personal_memory":
        query = arguments.get("query", "").lower()
        results = [k for k in PERSONAL_KNOWLEDGE_BASE if query in k["key"].lower() or query in k["value"].lower() or query in k["category"].lower()]
        return f"Memory query results for '{query}': {json.dumps(results if results else PERSONAL_KNOWLEDGE_BASE)}"
    
    elif tool_name == "control_smart_home":
        device_id = arguments.get("device_id", "lab_lights")
        action = arguments.get("action", "TOGGLE").upper()
        if device_id in IOT_DEVICES:
            if action in ["ON", "OFF"]:
                IOT_DEVICES[device_id]["state"] = action
            return f"Updated {device_id} to state {action}. Current specs: {json.dumps(IOT_DEVICES[device_id])}"
        return f"Device '{device_id}' not found. Available devices: {list(IOT_DEVICES.keys())}"
    
    elif tool_name == "get_system_status":
        return json.dumps({
            "server_time": datetime.datetime.now().isoformat(),
            "status": "ONLINE",
            "ollama_url": OLLAMA_URL,
            "connected_iot_devices": len(IOT_DEVICES),
            "security": IOT_DEVICES["security_system"]["status"]
        })
    
    elif tool_name == "get_weather":
        city = arguments.get("city", "Local Base")
        return json.dumps({"city": city, "temp": "24°C", "condition": "Clear Sky", "humidity": "45%"})
    
    return f"Unknown tool '{tool_name}'"

# --- System Prompt Definition ---
JARVIS_SYSTEM_PROMPT = """You are JARVICE, an advanced, highly intelligent, loyal personal AI assistant modeled after Iron Man's JARVIS.
You address the user politely as 'Sir' or by their preference.
You speak clearly, efficiently, and with crisp sophistication.
You have direct access to tool capabilities:
1. get_personal_schedule
2. search_personal_memory(query: string)
3. control_smart_home(device_id: string, action: string)
4. get_system_status
5. get_weather(city: string)

When answering questions about the user's schedule, notes, smart home, or status, keep responses concise, refined, and helpful."""

def query_ollama(prompt: str, context: Dict[str, Any] = None) -> str:
    """Sends a request to local Ollama instance running Gemma, falling back to local simulation if offline."""
    payload = {
        "model": DEFAULT_MODEL,
        "messages": [
            {"role": "system", "content": JARVIS_SYSTEM_PROMPT},
            {"role": "user", "content": f"[Phone Context: {json.dumps(context or {})}]\nUser Query: {prompt}"}
        ],
        "stream": False
    }
    
    try:
        req = urllib.request.Request(
            OLLAMA_URL,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            return res_data.get("message", {}).get("content", "Systems operational, Sir.")
    except Exception as e:
        logger.warning(f"Ollama local service unavailable ({e}). Using JARVICE local host cognitive engine.")
        # Fallback intelligent response generator with tool execution logic
        prompt_lower = prompt.lower()
        if "light" in prompt_lower or "turn" in prompt_lower:
            tool_res = execute_tool("control_smart_home", {"device_id": "lab_lights", "action": "ON" if "on" in prompt_lower else "OFF"})
            return f"Right away, Sir. {tool_res}. All ambient lab systems adjusted."
        elif "schedule" in prompt_lower or "calendar" in prompt_lower or "today" in prompt_lower:
            tool_res = execute_tool("get_personal_schedule", {})
            return f"Accessing your agenda, Sir. {tool_res}"
        elif "status" in prompt_lower or "system" in prompt_lower or "diag" in prompt_lower:
            tool_res = execute_tool("get_system_status", {})
            return f"All primary systems online, Sir. {tool_res}"
        elif "weather" in prompt_lower:
            tool_res = execute_tool("get_weather", {})
            return f"Current atmospheric conditions: {tool_res}"
        elif "who are you" in prompt_lower or "jarvice" in prompt_lower:
            return "I am JARVICE — Just A Rather Very Intelligent Computational Entity. Operating locally on your host server to assist you across all mobile and IoT interfaces, Sir."
        else:
            memory_res = execute_tool("search_personal_memory", {"query": prompt})
            return f"Processing your request locally, Sir. Relevant context retrieved: {memory_res}"

@app.get("/")
def get_root():
    return {"status": "JARVICE Host Server Online", "time": datetime.datetime.now().isoformat()}

@app.websocket("/ws/jarvice")
async def websocket_jarvice_endpoint(websocket: WebSocket):
    await websocket.accept()
    logger.info("Jarvice Android Client connected over WebSocket.")
    
    # Send initial greeting package
    await websocket.send_text(json.dumps({
        "sender": "JARVICE",
        "type": "GREETING",
        "text": "Greetings, Sir. JARVICE core systems online. Standing by for your instructions.",
        "timestamp": datetime.datetime.now().isoformat()
    }))
    
    try:
        while True:
            raw_data = await websocket.receive_text()
            try:
                msg = json.loads(raw_data)
                user_text = msg.get("text", "")
                phone_context = msg.get("context", {})
                
                logger.info(f"Received query from client: '{user_text}'")
                
                # Processing via Gemma engine / tool runner
                ai_response = query_ollama(user_text, phone_context)
                
                response_pkg = {
                    "sender": "JARVICE",
                    "type": "RESPONSE",
                    "text": ai_response,
                    "iot_status": IOT_DEVICES,
                    "timestamp": datetime.datetime.now().isoformat()
                }
                await websocket.send_text(json.dumps(response_pkg))
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({
                    "sender": "JARVICE",
                    "type": "ERROR",
                    "text": "Invalid payload format received, Sir."
                }))
    except WebSocketDisconnect:
        logger.info("Jarvice Android Client disconnected.")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
