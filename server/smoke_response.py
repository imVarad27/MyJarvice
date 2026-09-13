"""Read-only live server smoke check; never prints the pairing token."""
import asyncio
import json
import os
import time
from pathlib import Path
import websockets


async def main():
    config = {}
    for line in Path(__file__).with_name(".env").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            config[key.strip()] = value.strip().strip('\"').strip("'")
    token = os.environ.get("JARVIS_API_TOKEN", config.get("JARVIS_API_TOKEN", config.get("JARVICE_API_TOKEN", "jarvis_local_token")))
    async with websockets.connect("ws://127.0.0.1:8000/ws/jarvis", additional_headers={"Authorization": "Bearer " + token}, proxy=None) as ws:
        await ws.recv()
        for question in ["Explain photosynthesis in two sentences.", "show my tasks"]:
            start = time.perf_counter()
            partials = 0
            first = None
            await ws.send(json.dumps({"query": question, "voice_id": "native_android", "stream_response": True}))
            while True:
                msg = json.loads(await asyncio.wait_for(ws.recv(), 130))
                if msg.get("type") == "PARTIAL":
                    partials += 1
                    first = first or time.perf_counter() - start
                else:
                    assert msg.get("type") != "ERROR", msg.get("text")
                    assert msg.get("reply_id")
                    assert partials > 0
                    print(json.dumps({"question": question, "partials": partials, "first_seconds": round(first, 3), "total_seconds": round(time.perf_counter() - start, 3), "reply": msg.get("text")}), flush=True)
                    break


if __name__ == "__main__":
    asyncio.run(main())
