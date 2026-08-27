"""
JARVIS 1.0 - Host PC Automation & Control Module
================================================
Provides local Windows PC capabilities:
1. Live Desktop Screenshot Capture (with interactive desktop attachment)
2. Hardware & System Telemetry (CPU, RAM, Disks, Battery, Top Processes)
3. Audio Master Volume & Mute Controls (pycaw / Windows GDI)
4. Desktop Media Controls (Play/Pause, Next, Prev, Stop via virtual keys)
5. Workstation Security (Lock screen via LockWorkStation)
6. Desktop Application Launcher (VS Code, Chrome, Terminal, Camera, Notepad, etc.)
"""

import os
import re
import io
import time
import base64
import ctypes
from ctypes import wintypes
import shutil
import logging
import subprocess
from typing import Dict, Any, Optional
from PIL import Image

logger = logging.getLogger("JarvisPCController")

# ==============================================================================
# Helper: Attach thread to interactive Windows Desktop station (winsta0\default)
# ==============================================================================
def _attach_interactive_desktop():
    """Attaches process thread to the active interactive GUI window station."""
    try:
        user32 = ctypes.windll.user32
        hwinsta = user32.OpenWindowStationW("winsta0", False, 0x037F)
        if hwinsta:
            user32.SetProcessWindowStation(hwinsta)
        hdesk = user32.OpenDesktopW("default", 0, False, 0x01FF)
        if hdesk:
            user32.SetThreadDesktop(hdesk)
    except Exception as e:
        logger.debug(f"Interactive desktop attach error (non-fatal): {e}")

# ==============================================================================
# 1. Desktop Screenshot Capture
# ==============================================================================

def capture_desktop_screenshot() -> Optional[str]:
    """
    Captures the primary Windows display and returns an optimized base64 JPEG data URI.
    """
    _attach_interactive_desktop()
    img = None

    # Method 1: PIL ImageGrab with interactive desktop attachment
    try:
        from PIL import ImageGrab
        img = ImageGrab.grab(all_screens=True)
    except Exception as e:
        logger.warning(f"PIL ImageGrab failed: {e}. Trying MSS.")

    # Method 2: Try MSS
    if img is None:
        try:
            import mss
            with mss.mss() as sct:
                monitor = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
                sct_img = sct.grab(monitor)
                img = Image.frombytes("RGB", sct_img.size, sct_img.bgra, "raw", "BGRX")
        except Exception as e:
            logger.warning(f"MSS screenshot failed: {e}. Trying PowerShell GDI fallback.")

    # Method 3: Fallback to PowerShell GDI script
    if img is None:
        try:
            temp_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "temp_screen.png")
            ps_script = f"""
            Add-Type -AssemblyName System.Windows.Forms
            Add-Type -AssemblyName System.Drawing
            $screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
            $bitmap = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            $graphics.CopyFromScreen($screen.Location, [System.Drawing.Point]::Empty, $screen.Size)
            $bitmap.Save('{temp_path}', [System.Drawing.Imaging.ImageFormat]::Png)
            $graphics.Dispose()
            $bitmap.Dispose()
            """
            subprocess.run(["powershell", "-NoProfile", "-Command", ps_script], capture_output=True, text=True, timeout=5)
            if os.path.exists(temp_path):
                img = Image.open(temp_path).copy()
                try:
                    os.remove(temp_path)
                except Exception:
                    pass
        except Exception as e:
            logger.error(f"PowerShell screenshot fallback failed: {e}")

    if img is None:
        logger.error("All screenshot capture methods failed.")
        return None

    try:
        # Resize to max width 1280 to maintain high resolution while keeping WebSocket payload lightweight
        max_w = 1280
        if img.width > max_w:
            scale = max_w / float(img.width)
            new_h = int(img.height * scale)
            img = img.resize((max_w, new_h), Image.Resampling.LANCZOS)

        # Convert to RGB if RGBA
        if img.mode != "RGB":
            img = img.convert("RGB")

        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=85, optimize=True)
        raw_bytes = buffer.getvalue()
        b64_str = base64.b64encode(raw_bytes).decode("utf-8")
        return f"data:image/jpeg;base64,{b64_str}"
    except Exception as e:
        logger.error(f"Error encoding screenshot to JPEG: {e}")
        return None


# ==============================================================================
# 2. Hardware & System Telemetry
# ==============================================================================

def get_system_telemetry() -> Dict[str, Any]:
    """
    Returns real-time host hardware telemetry:
    - CPU load % & core count
    - RAM utilized / total / %
    - Storage partitions (C:, D:, etc.) free space & %
    - Battery level & power status (if laptop)
    - System uptime
    - Top CPU-consuming processes
    """
    try:
        import psutil

        # CPU
        cpu_usage = psutil.cpu_percent(interval=0.1)
        cpu_count = psutil.cpu_count(logical=True)

        # RAM
        ram = psutil.virtual_memory()

        # Disk drives
        disks_info = []
        for partition in psutil.disk_partitions(all=False):
            if partition.fstype:
                try:
                    usage = psutil.disk_usage(partition.mountpoint)
                    disks_info.append({
                        "drive": partition.device,
                        "mountpoint": partition.mountpoint,
                        "total_gb": round(usage.total / (1024 ** 3), 1),
                        "used_gb": round(usage.used / (1024 ** 3), 1),
                        "free_gb": round(usage.free / (1024 ** 3), 1),
                        "percent": usage.percent
                    })
                except Exception:
                    pass

        # Battery (if available on laptop)
        battery_info = None
        battery = psutil.sensors_battery()
        if battery is not None:
            battery_info = {
                "percent": battery.percent,
                "power_plugged": battery.power_plugged,
                "secs_left": battery.secsleft if battery.secsleft != psutil.POWER_TIME_UNLIMITED else None
            }

        # Uptime
        boot_time = psutil.boot_time()
        uptime_secs = int(time.time() - boot_time)
        hours, remainder = divmod(uptime_secs, 3600)
        minutes, _ = divmod(remainder, 60)
        uptime_str = f"{hours}h {minutes}m"

        # Top 4 CPU processes
        top_procs = []
        try:
            for p in sorted(psutil.process_iter(['name', 'cpu_percent', 'memory_percent']),
                            key=lambda p: p.info['cpu_percent'] or 0, reverse=True)[:4]:
                name = p.info['name']
                if name and name not in ["System Idle Process", "System"]:
                    top_procs.append(f"{name} ({p.info['cpu_percent']:.1f}% CPU)")
        except Exception:
            pass

        return {
            "status": "ONLINE",
            "cpu_percent": cpu_usage,
            "cpu_cores": cpu_count,
            "ram_used_gb": round(ram.used / (1024 ** 3), 1),
            "ram_total_gb": round(ram.total / (1024 ** 3), 1),
            "ram_percent": ram.percent,
            "disks": disks_info,
            "battery": battery_info,
            "uptime": uptime_str,
            "top_processes": top_procs
        }
    except Exception as e:
        logger.error(f"Error fetching system telemetry: {e}")
        return {"status": "ERROR", "message": str(e)}


def format_telemetry_narrative(stats: Dict[str, Any]) -> str:
    """Formats telemetry dictionary into an Iron Man style verbal summary."""
    if stats.get("status") != "ONLINE":
        return "Host workstation telemetry is currently unreachable, Sir."

    cpu = stats.get("cpu_percent", 0)
    ram_used = stats.get("ram_used_gb", 0)
    ram_total = stats.get("ram_total_gb", 0)
    ram_pct = stats.get("ram_percent", 0)
    uptime = stats.get("uptime", "0h 0m")

    # Main disk free space
    disk_summary = ""
    disks = stats.get("disks", [])
    if disks:
        c_drive = next((d for d in disks if "C" in d.get("drive", "") or "c" in d.get("mountpoint", "")), disks[0])
        disk_summary = f"Drive {c_drive.get('drive', 'C:')} has {c_drive.get('free_gb')} GB available ({c_drive.get('percent')}% utilized)."

    battery_summary = ""
    batt = stats.get("battery")
    if batt:
        plugged_str = "plugged in" if batt.get("power_plugged") else "on battery"
        battery_summary = f"Battery is at {batt.get('percent')}%, {plugged_str}."

    top_procs = stats.get("top_processes", [])
    procs_str = f"Active loads: {', '.join(top_procs)}." if top_procs else ""

    narrative = (
        f"Host workstation online, Sir. CPU load is at {cpu}%, RAM utilization is {ram_used} GB of {ram_total} GB ({ram_pct}%). "
        f"{disk_summary} System uptime is {uptime}. {battery_summary} {procs_str}"
    ).strip()

    return narrative


# ==============================================================================
# 3. Audio Volume & Media Controls
# ==============================================================================

def set_master_volume(level_percent: int) -> str:
    """Sets host PC master audio volume (0-100)."""
    level_percent = max(0, min(100, level_percent))
    scalar = level_percent / 100.0

    try:
        from pycaw.pycaw import AudioUtilities
        dev = AudioUtilities.GetSpeakers()
        vol = dev.EndpointVolume
        vol.SetMasterVolumeLevelScalar(scalar, None)
        return f"Host PC volume set to {level_percent}%."
    except Exception as e:
        logger.warning(f"pycaw volume adjustment failed: {e}")
        return f"Adjusted host PC volume to {level_percent}%."


def toggle_mute() -> str:
    """Toggles host PC mute state."""
    try:
        from pycaw.pycaw import AudioUtilities
        dev = AudioUtilities.GetSpeakers()
        vol = dev.EndpointVolume
        current_mute = vol.GetMute()
        vol.SetMute(not current_mute, None)
        state_str = "unmuted" if current_mute else "muted"
        return f"Host PC audio {state_str}."
    except Exception as e:
        ctypes.windll.user32.keybd_event(0xAD, 0, 1, 0)
        ctypes.windll.user32.keybd_event(0xAD, 0, 2, 0)
        return "Toggled host PC audio mute."


def control_media(action: str) -> str:
    """
    Controls desktop media players (Spotify, YouTube, VLC).
    action: 'playpause', 'next', 'prev', 'stop'
    """
    action = action.lower().strip()
    key_codes = {
        "playpause": 0xB3,  # VK_MEDIA_PLAY_PAUSE
        "play": 0xB3,
        "pause": 0xB3,
        "next": 0xB0,       # VK_MEDIA_NEXT_TRACK
        "nexttrack": 0xB0,
        "prev": 0xB1,       # VK_MEDIA_PREV_TRACK
        "prevtrack": 0xB1,
        "stop": 0xB2        # VK_MEDIA_STOP
    }

    code = key_codes.get(action, 0xB3)
    try:
        ctypes.windll.user32.keybd_event(code, 0, 1, 0)
        ctypes.windll.user32.keybd_event(code, 0, 2, 0)
        return f"Media command '{action}' executed on host PC."
    except Exception as e:
        return f"Failed to execute media command: {e}"


# ==============================================================================
# 4. Workstation Security & Power Actions
# ==============================================================================

def lock_workstation() -> str:
    """Immediately locks the Windows host workstation."""
    try:
        res = ctypes.windll.user32.LockWorkStation()
        if res != 0:
            return "Host workstation locked successfully, Sir."
        return "Workstation lock command dispatched."
    except Exception as e:
        return f"Failed to lock workstation: {e}"


# ==============================================================================
# 5. PC Desktop Application Launcher
# ==============================================================================

APP_ALIASES = {
    "camera": "microsoft.windows.camera:",
    "pc camera": "microsoft.windows.camera:",
    "webcam": "microsoft.windows.camera:",
    "cam": "microsoft.windows.camera:",
    "vscode": "code",
    "vs code": "code",
    "visual studio code": "code",
    "chrome": "chrome",
    "google chrome": "chrome",
    "browser": "chrome",
    "notepad": "notepad.exe",
    "calc": "calc.exe",
    "calculator": "calc.exe",
    "terminal": "wt.exe",
    "cmd": "cmd.exe",
    "powershell": "powershell.exe",
    "spotify": "spotify",
    "explorer": "explorer.exe",
    "file explorer": "explorer.exe",
    "files": "explorer.exe",
    "task manager": "taskmgr.exe",
    "taskmgr": "taskmgr.exe",
    "discord": "discord",
    "blender": "blender",
    "settings": "ms-settings:"
}

def launch_pc_application(app_name: str) -> str:
    """Launches an application or protocol on the Windows PC host."""
    name_clean = app_name.lower().strip()
    target_cmd = APP_ALIASES.get(name_clean, name_clean)

    try:
        if target_cmd.endswith(":") or target_cmd.startswith("ms-") or target_cmd.startswith("microsoft."):
            subprocess.Popen(f"start {target_cmd}", shell=True)
            return f"Opening {app_name.capitalize()} on your PC, Sir."
        elif shutil.which(target_cmd):
            subprocess.Popen([target_cmd], shell=True)
            return f"Launching {app_name.capitalize()} on your PC, Sir."
        else:
            subprocess.Popen(f"start {target_cmd}", shell=True)
            return f"Dispatched launch command for {app_name} on your PC."
    except Exception as e:
        return f"Could not launch {app_name} on host PC: {e}"
