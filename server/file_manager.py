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


def get_allowed_roots() -> List[str]:
    """Directories which the paired phone is permitted to explore.

    A phone must not be able to turn a valid pairing token into unrestricted
    host file-system access.  Keep this small and make new roots an explicit
    product decision.
    """
    presets = get_preset_paths()
    return [
        os.path.realpath(os.path.abspath(path))
        for path in (*presets.values(), ensure_drop_dir())
    ]


def _canonical(path: str) -> str:
    return os.path.normcase(os.path.realpath(os.path.abspath(path)))


def is_allowed_path(path: str) -> bool:
    candidate = _canonical(path)
    for root in get_allowed_roots():
        try:
            if os.path.commonpath([candidate, _canonical(root)]) == _canonical(root):
                return True
        except ValueError:  # Different Windows drive letters.
            continue
    return False


def require_allowed_existing_path(path: str, *, directory: bool = False) -> str:
    if not path:
        raise ValueError("A path is required")
    resolved = _canonical(path)
    if not os.path.exists(resolved):
        raise FileNotFoundError("Path not found")
    if directory and not os.path.isdir(resolved):
        raise NotADirectoryError("A folder is required")
    if not is_allowed_path(resolved):
        raise PermissionError("That path is outside Jarvis's approved PC folders")
    return resolved


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
    target_dir = (
        require_allowed_existing_path(destination_dir, directory=True)
        if destination_dir else ensure_drop_dir()
    )

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
    if preset:
        if preset.lower() not in presets:
            raise ValueError("Unknown PC folder shortcut")
        target_path = require_allowed_existing_path(presets[preset.lower()], directory=True)
    elif path:
        target_path = require_allowed_existing_path(path, directory=True)
    else:
        target_path = require_allowed_existing_path(presets["projects"], directory=True)

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
    if parent_path == target_path or not is_allowed_path(parent_path):
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
    try:
        path = require_allowed_existing_path(path)
    except (FileNotFoundError, PermissionError, ValueError) as exc:
        return {"status": "error", "message": str(exc)}

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
