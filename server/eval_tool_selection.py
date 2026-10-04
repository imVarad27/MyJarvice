"""Opt-in live selection check using synthetic requests; NEVER executes tools.

Run manually: python eval_tool_selection.py --model gemma4-e4b
No personal chat, files, memory, notifications or credentials are loaded.
"""
import argparse
import time

import assistant_runtime
import model_tool_router as router
from agent_loop import TOOL_POLICY, run, ToolResult


CASES = [
    ("Could you check how much charge my handset has left?", "phone_status"),
    ("How is the workstation doing for RAM and CPU right now?", "pc_status"),
    ("Put buying groceries on my PC to-do list.", "add_task"),
    ("Bring up Notepad on the computer, please.", "open_pc_app"),
    ("Why do phone batteries degrade over time?", None),
    ("How would I send an email safely? Explain only; don't create a draft.", None),
    ("What are the latest Android developer news headlines?", "search_web"),
    ("Remind me to stretch in 20 minutes.", "add_reminder"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="gemma4-e4b")
    options = parser.parse_args()
    passed = 0
    for query, expected in CASES:
        start = time.monotonic()
        messages = [{"role": "system", "content": "You are a helpful assistant. " + TOOL_POLICY +
                     "\nHost time: 2026-10-04T16:00:00+05:30. User name unknown."},
                    {"role": "user", "content": query}]
        message = assistant_runtime.generate_message(messages, options.model,
            "http://localhost:11434/api/chat", 120, tools=router.ollama_tools())
        decision = router.decision_from_message(message)
        actual = decision.name if decision else ("INVALID" if message.get("tool_calls") else None)
        ok = actual == expected
        passed += ok
        print(f"{'PASS' if ok else 'FAIL'} | expected={expected} selected={actual} | {time.monotonic()-start:.1f}s | {query}", flush=True)
    # A full read-only turn with synthetic telemetry: no phone/OS APIs are used.
    invoked = []
    def infer(messages, schemas):
        return assistant_runtime.generate_message(messages, options.model,
            "http://localhost:11434/api/chat", 120, tools=schemas)
    def observe(decision):
        invoked.append(decision.name)
        if decision.name != "phone_status":
            raise ValueError("Only synthetic phone telemetry is available")
        return ToolResult('{"battery_level":"57%","is_charging":true,"connection_type":"Wi-Fi"}')
    grounded = run([{"role": "system", "content": "Be concise. " + TOOL_POLICY},
                    {"role": "user", "content": "How much charge does my phone have left?"}],
                   infer, observe, lambda *args: None)
    grounded_ok = invoked == ["phone_status"] and "57" in grounded.reply
    print(f"{'PASS' if grounded_ok else 'FAIL'} | synthetic telemetry grounding | {grounded.reply}", flush=True)
    print(f"{passed}/{len(CASES)} synthetic selection checks passed. No real tools executed.")
    raise SystemExit(0 if passed == len(CASES) and grounded_ok else 1)


if __name__ == "__main__":
    main()
