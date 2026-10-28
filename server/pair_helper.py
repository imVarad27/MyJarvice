import os
import socket
import urllib.parse
from typing import List, Dict, Any


def get_local_ips() -> List[str]:
    """Finds non-loopback IPv4 addresses of this host."""
    ips = []
    # Primary route socket check
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        primary = s.getsockname()[0]
        s.close()
        if primary and primary != "127.0.0.1" and primary not in ips:
            ips.append(primary)
    except Exception:
        pass

    # Hostname enumeration fallback
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip and not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except Exception:
        pass

    if not ips:
        ips.append("127.0.0.1")
    return ips


def load_token(env_dir: str = None) -> str:
    """Loads JARVICE_API_TOKEN or JARVIS_API_TOKEN from server environment or .env file."""
    token = os.environ.get("JARVIS_API_TOKEN", os.environ.get("JARVICE_API_TOKEN", "")).strip()
    if token:
        return token
    if env_dir is None:
        env_dir = os.path.dirname(os.path.abspath(__file__))
    env_file = os.path.join(env_dir, ".env")
    if os.path.exists(env_file):
        try:
            with open(env_file, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line.startswith("JARVICE_API_TOKEN=") or line.startswith("JARVIS_API_TOKEN="):
                        val = line.split("=", 1)[1].strip().strip('"').strip("'")
                        if val:
                            return val
        except Exception:
            pass
    return ""


def generate_pairing_uri(ip: str, token: str, port: int = 8000) -> str:
    """Constructs a standard jarvis-pair:// URL for phone import."""
    host_str = f"http://{ip}:{port}"
    q_host = urllib.parse.quote(host_str, safe="")
    q_token = urllib.parse.quote(token, safe="")
    return f"jarvis-pair://v1?host={q_host}&token={q_token}"


def generate_pairing_payload(ip: str, token: str, port: int = 8000) -> Dict[str, str]:
    """Generates a JSON pairing snippet."""
    return {
        "host": f"http://{ip}:{port}",
        "token": token
    }


def main():
    token = load_token()
    ips = get_local_ips()
    port = int(os.environ.get("JARVIS_PORT", "8000"))

    print("=" * 60)
    print(" 🤖 Jarvis PC Pairing Onboarding Helper")
    print("=" * 60)
    if not token:
        print("\n⚠️  No pairing token found in server/.env!")
        print("Please set JARVICE_API_TOKEN in server/.env before pairing.")
        return

    print(f"\nDiscovered Local Host IP(s): {', '.join(ips)}")
    print(f"Port: {port}\n")

    for idx, ip in enumerate(ips, 1):
        uri = generate_pairing_uri(ip, token, port)
        print(f"--- Option {idx}: {ip} ---")
        print(f"Pairing Code:\n{uri}\n")

    print("Instructions:")
    print("1. In Jarvis Android, open Settings → PC connection.")
    print("2. Tap 'Paste setup code' and paste the Pairing Code above.")
    print("3. Jarvis will verify reachability and configure connection automatically.\n")


if __name__ == "__main__":
    main()
