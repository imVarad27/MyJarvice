"""Read-only local model benchmark. Does not access personal memory or perform actions."""
import ast
import json
import time
from pathlib import Path
import assistant_runtime


def main():
    tree = ast.parse(Path(__file__).with_name("main.py").read_text(encoding="utf-8-sig"))
    prompt = next(ast.literal_eval(node.value) for node in tree.body
                  if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "JARVIS_SYSTEM_PROMPT" for t in node.targets))
    for question in ["Hi Jarvis, help me focus on studying tonight.", "Explain why the sky looks blue in two sentences."]:
        start = time.perf_counter()
        first = []
        def update(text):
            if not first:
                first.append(time.perf_counter() - start)
        answer = assistant_runtime.generate([
            {"role": "system", "content": prompt}, {"role": "user", "content": question}
        ], "gemma4-e4b", "http://localhost:11434/api/chat", 120, update)
        print(json.dumps({"question": question, "first_text_seconds": round(first[0], 3) if first else None,
                          "total_seconds": round(time.perf_counter() - start, 3), "answer": answer}), flush=True)


if __name__ == "__main__":
    main()
