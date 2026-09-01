"""
JARVIS 1.0 - Bi-Directional Wireless File Transfer & PC Explorer Module
=======================================================================
Provides safe remote PC file browsing, streaming file downloads,
phone-to-PC file drops (AirDrop style), and remote file/folder launch.
"""

import os
import datetime
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("JarvisFileManager")

# Default Drop Directory
DROP_DIR = os.path.join(os.path.expanduser("~/Downloads"), "JarvisDrop")


def get_preset_paths() -> Dict[str, str]:
    """Returns mapped standard user directory shortcuts on Windows."""
    user_home = os.path.expanduser("~")
    return {
        "downloads": os.path.join(user_home, "Downloads"),
        "documents": os.path.join(user_home, "Documents"),
        "desktop": os.path.join(user_home, "Desktop"),
        "projects": os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    }


def ensure_drop_dir() -> str:
    """Ensures the ~/Downloads/JarvisDrop folder exists."""
    if not os.path.exists(DROP_DIR):
        os.makedirs(DROP_DIR, exist_ok=True)
    return DROP_DIR


def save_uploaded_file(file_bytes: bytes, filename: str, destination_dir: Optional[str] = None) -> Dict[str, Any]:
    """
    Saves an uploaded file to the drop directory.
    Handles duplicate filenames by appending timestamp/counter.
    """
    target_dir = destination_dir if destination_dir and os.path.exists(destination_dir) else ensure_drop_dir()

    # Clean filename
    clean_name = os.path.basename(filename).strip()
    if not clean_name:
        clean_name = f"drop_{int(datetime.datetime.now().timestamp())}.bin"

    base_name, ext = os.path.splitext(clean_name)
    target_path = os.path.join(target_dir, clean_name)

    counter = 1
    while os.path.exists(target_path):
        target_path = os.path.join(target_dir, f"{base_name}_{counter}{ext}")
        counter += 1

    with open(target_path, "wb") as f:
        f.write(file_bytes)

    size = len(file_bytes)
    logger.info("Saved dropped file: '%s' (%d bytes) to %s", os.path.basename(target_path), size, target_path)

    return {
        "status": "success",
        "filename": os.path.basename(target_path),
        "saved_path": target_path,
        "size_bytes": size,
        "message": f"Saved '{os.path.basename(target_path)}' to PC Downloads/JarvisDrop"
    }


def browse_directory(path: Optional[str] = None, preset: Optional[str] = None) -> Dict[str, Any]:
    """
    Safely lists contents of a PC directory.
    Returns current path, parent path, presets, and sorted file/directory items.
    """
    presets = get_preset_paths()

    target_path = None
    if preset and preset.lower() in presets:
        target_path = presets[preset.lower()]
    elif path and os.path.exists(path):
        target_path = os.path.abspath(path)
    else:
        target_path = presets["projects"]

    if not os.path.exists(target_path):
        target_path = presets["downloads"]

    entries = []
    try:
        with os.scandir(target_path) as it:
            for entry in it:
                try:
                    # Skip hidden/system files and build folders
                    if entry.name.startswith(".") or entry.name in ["__pycache__", "node_modules", ".gradle"]:
                        continue

                    stat = entry.stat()
                    is_dir = entry.is_dir()
                    entries.append({
                        "name": entry.name,
                        "path": os.path.abspath(entry.path),
                        "is_dir": is_dir,
                        "size_bytes": stat.st_size if not is_dir else 0,
                        "mtime": datetime.datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M"),
                        "ext": os.path.splitext(entry.name)[1].lower() if not is_dir else ""
                    })
                except (PermissionError, FileNotFoundError):
                    continue
    except Exception as e:
        logger.warning("Error scanning directory '%s': %s", target_path, e)

    # Sort directories first, then files alphabetically
    entries.sort(key=lambda x: (not x["is_dir"], x["name"].lower()))

    parent_path = os.path.dirname(target_path)
    if parent_path == target_path:  # At root drive (e.g. D:\)
        parent_path = None

    return {
        "status": "success",
        "current_path": target_path,
        "parent_path": parent_path,
        "presets": presets,
        "total_entries": len(entries),
        "entries": entries
    }


def open_path_on_pc(path: str) -> Dict[str, Any]:
    """Launches a file or folder on the host PC using the default Windows application."""
    if not os.path.exists(path):
        return {"status": "error", "message": f"Path not found: {path}"}

    try:
        os.startfile(os.path.abspath(path))
        logger.info("Opened on host PC: '%s'", path)
        return {
            "status": "success",
            "opened_path": path,
            "message": f"Opened on host PC: {os.path.basename(path) or path}"
        }
    except Exception as e:
        logger.error("Failed to open '%s' on PC: %s", path, e)
        return {"status": "error", "message": str(e)}
