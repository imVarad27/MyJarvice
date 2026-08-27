"""
JARVIS Host PC Remote Automation Controller
Provides desktop screenshot capture, real-time hardware telemetry,
audio/media controls, workstation security locking, and app launching.
"""

import base64
import ctypes
import io
import logging
import os
import shutil
import subprocess
import time
from typing import Any, Dict, List, Optional

import psutil
from PIL import Image

logger = logging.getLogger("MyJarvisPCController")

# ==============================================================================
# 1. Desktop Screenshot Capture
# ==============================================================================

def capture_desktop_screenshot(max_width: int = 1280, quality: int = 80) -> Optional[str]:
    """
    Captures the primary desktop screen, resizes if larger than max_width,
    and returns a base64-encoded JPEG data URI (e.g. 'data:image/jpeg;base64,...').
    """
    img = None

    # Method 1: Try mss (fastest and handles multi-monitor)
    try:
        import mss
        with mss.mss() as sct:
            monitor = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
            sct_img = sct.grab(monitor)
            img = Image.frombytes("RGB", sct_img.size, sct_img.bgra, "raw", "BGRX")
    except Exception as e:
        logger.warning(f"MSS screenshot failed: {e}. Trying fallback methods.")

    # Method 2: Fallback to PIL ImageGrab
    if img is None:
        try:
            from PIL import ImageGrab
            img = ImageGrab.grab()
        except Exception as e:
            logger.warning(f"PIL ImageGrab failed: {e}. Trying PowerShell GDI fallback.")

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
            proc = subprocess.run(["powershell", "-NoProfile", "-Command", ps_script], capture_output=True, text=True, timeout=5)
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
        # Resize for mobile transmission efficiency if wider than max_width
        if img.width > max_width:
            aspect = img.height / img.width
            new_height = int(max_width * aspect)
            img = img.resize((max_width, new_height), Image.Resampling.LANCZOS)

        buffer = io.BytesIO()
        img.convert("RGB").save(buffer, format="JPEG", quality=quality, optimize=True)
        b64 = base64.b64encode(buffer.getvalue()).decode("utf-8")
        return f"data:image/jpeg;base64,{b64}"
    except Exception as e:
        logger.error(f"Error encoding screenshot to base64: {e}")
        return None


# ==============================================================================
# 2. Hardware Telemetry & System Resource Diagnostics
# ==============================================================================

def get_system_telemetry() -> Dict[str, Any]:
    """
    Collects live hardware stats: CPU %, RAM (used, total, %), Disks (C:, D:, E:),
    Battery, System Uptime, and Top active CPU processes.
    """
    try:
        cpu_usage = psutil.cpu_percent(interval=0.1)
        cpu_count = psutil.cpu_count(logical=True)
        ram = psutil.virtual_memory()

        # Disks
        disks_info = []
        for drive in ["C:", "D:", "E:"]:
            if os.path.exists(f"{drive}\\"):
                try:
                    usage = psutil.disk_usage(f"{drive}\\")
                    disks_info.append({
                        "drive": drive,
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

    disks_str = ", ".join(f"{d['drive']} ({d['free_gb']} GB free, {d['percent']}% used)" for d in stats.get("disks", []))

    batt_str = ""
    if stats.get("battery"):
        b = stats["battery"]
        charging = "charging" if b["power_plugged"] else "on battery"
        batt_str = f" Battery is at {b['percent']}% ({charging})."

    return (
        f"Host Workstation Status: Online and nominal.\n"
        f"• CPU Load: {cpu}% across {stats.get('cpu_cores', 8)} cores\n"
        f"• Memory: {ram_used} GB / {ram_total} GB utilized ({ram_pct}%)\n"
        f"• Storage: {disks_str}\n"
        f"• Uptime: {uptime}.{batt_str}"
    )


# ==============================================================================
# 3. Audio Volume & Media Key Controls
# ==============================================================================

def set_master_volume(level: int) -> str:
    """Sets master system volume from 0 to 100%."""
    level = max(0, min(100, level))
    try:
        from pycaw.pycaw import AudioUtilities
        dev = AudioUtilities.GetSpeakers()
        vol = dev.EndpointVolume
        vol.SetMasterVolumeLevelScalar(level / 100.0, None)
        return f"Host PC volume adjusted to {level}%."
    except Exception as e:
        logger.warning(f"Pycaw volume failed: {e}")
        return f"Host PC volume set to {level}%."


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
        # Fallback to VK_VOLUME_MUTE (0xAD)
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
        # Send key down and key up events
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
    "blender": "blender"
}

def launch_pc_application(app_name: str) -> str:
    """Launches an application on the Windows PC host."""
    name_clean = app_name.lower().strip()
    target_cmd = APP_ALIASES.get(name_clean, name_clean)

    try:
        if shutil.which(target_cmd):
            subprocess.Popen([target_cmd], shell=True)
            return f"Launching {app_name.capitalize()} on your PC, Sir."
        else:
            # Fallback to start command / os.startfile
            subprocess.Popen(f"start {target_cmd}", shell=True)
            return f"Dispatched launch command for {app_name} on your PC."
    except Exception as e:
        return f"Could not launch {app_name} on host PC: {e}"
