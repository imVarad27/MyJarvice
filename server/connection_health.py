"""Read-only bounded Ollama probes: no prompts, loading, downloads or user data."""
import json
import re
import urllib.request
from urllib.parse import urlsplit, urlunsplit

MAX_BYTES = 256 * 1024
MODEL_NAME = re.compile(r"[A-Za-z0-9._:/-]{1,120}\Z")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def names_match(configured, available):
    def normalise(value):
        return value if ":" in value.rsplit("/", 1)[-1] else value + ":latest"
    return normalise(configured) == normalise(available)


def probe_model(model, chat_url, fetch=None):
    report = {"runtime": "unknown", "model": model if MODEL_NAME.fullmatch(model) else "",
              "model_status": "unknown", "loaded": None}
    if not report["model"]:
        return report
    parts = urlsplit(chat_url)
    if parts.scheme not in ("http", "https") or not parts.netloc or parts.username or parts.password or parts.query or parts.fragment:
        return report
    origin = urlunsplit((parts.scheme, parts.netloc, "", "", ""))

    def get(path):
        if fetch is not None:
            payload = fetch(origin + path)
        else:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
            with opener.open(origin + path, timeout=2) as response:
                raw = response.read(MAX_BYTES + 1)
                if len(raw) > MAX_BYTES:
                    raise ValueError("Oversized Ollama response")
                payload = json.loads(raw)
        if not isinstance(payload, dict) or not isinstance(payload.get("models"), list) or len(payload["models"]) > 500:
            raise ValueError("Invalid Ollama model list")
        result = []
        for item in payload["models"]:
            if not isinstance(item, dict):
                raise ValueError("Invalid Ollama model")
            name = item.get("name", item.get("model"))
            if not isinstance(name, str) or not MODEL_NAME.fullmatch(name):
                raise ValueError("Invalid Ollama model name")
            result.append(name)
        return result

    try:
        installed = get("/api/tags")
    except (ValueError, UnicodeError, TypeError):
        report["runtime"] = "invalid_response"
        return report
    except Exception:
        report["runtime"] = "unreachable"
        return report
    report["runtime"] = "reachable"
    report["model_status"] = "installed" if any(names_match(model, name) for name in installed) else "missing"
    if report["model_status"] == "installed":
        try:
            report["loaded"] = any(names_match(model, name) for name in get("/api/ps"))
        except Exception:
            pass  # Older runtimes may not support ps; installation remains established.
    return report
