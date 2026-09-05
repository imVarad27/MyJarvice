"""
JARVIS 1.0 - Neural Studio Voice & Paul Bettany Voice Engine
============================================================
High-fidelity neural voice synthesis using Microsoft Neural voices with
instant local phrase caching, Paul Bettany British gentleman tone,
FRIDAY (Irish), and EDITH (American) neural profiles.
"""

import asyncio
import base64
import hashlib
import logging
import os
import re
from typing import Dict, Any, Optional

import edge_tts

logger = logging.getLogger("JarvisNeuralVoice")

CACHE_DIR = os.path.join(os.path.dirname(__file__), "voice_cache")
os.makedirs(CACHE_DIR, exist_ok=True)

VOICE_PROFILES: Dict[str, Dict[str, str]] = {
    "jarvis_classic": {
        "voice": "en-GB-RyanNeural",
        "rate": "+2%",
        "pitch": "-2Hz",
        "name": "JARVIS Classic (Paul Bettany British)",
        "description": "Calm, refined, authoritative British gentleman"
    },
    "jarvis_alfie": {
        "voice": "en-GB-AlfieNeural",
        "rate": "+4%",
        "pitch": "0Hz",
        "name": "JARVIS Alfie (British Dynamic)",
        "description": "Energetic modern British male"
    },
    "friday": {
        "voice": "en-IE-EmilyNeural",
        "rate": "+2%",
        "pitch": "+1Hz",
        "name": "FRIDAY (Irish Female Neural)",
        "description": "Sophisticated Irish female AI assistant"
    },
    "edith": {
        "voice": "en-US-AndrewNeural",
        "rate": "+3%",
        "pitch": "0Hz",
        "name": "EDITH (American Crisp Neural)",
        "description": "Modern tactical American male"
    }
}

DEFAULT_VOICE = "jarvis_classic"

# In-memory LRU cache for ultra-fast instant playback
MEMORY_CACHE: Dict[str, str] = {}


def _clean_text_for_speech(text: str) -> str:
    """Strips Markdown syntax, URLs, and code blocks for clean spoken narration."""
    # Remove code blocks
    text = re.sub(r"```[\s\S]*?```", " [Code block omitted] ", text)
    # Remove inline code
    text = re.sub(r"`([^`]+)`", r"\1", text)
    # Remove Markdown links [text](url) -> text
    text = re.sub(r"\[([^\]]+)\]\([^\)]+\)", r"\1", text)
    # Remove URLs
    text = re.sub(r"https?://\S+", "", text)
    # Remove bold/italic markers
    text = re.sub(r"[*_~#]", "", text)
    # Collapse multiple whitespaces
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _get_cache_key(text: str, voice_id: str) -> str:
    norm = f"{voice_id}:{text.strip().lower()}"
    return hashlib.md5(norm.encode("utf-8")).hexdigest()


async def synthesize_speech_async(text: str, voice_id: str = DEFAULT_VOICE) -> Optional[str]:
    """
    Synthesizes clean text into an MP3 audio base64 payload.
    Uses in-memory cache -> disk cache -> live edge_tts synthesis.
    """
    speech_text = _clean_text_for_speech(text)
    if not speech_text:
        return None

    # Limit maximum spoken characters for fast response (<500 chars)
    if len(speech_text) > 450:
        speech_text = speech_text[:440] + "..."

    profile = VOICE_PROFILES.get(voice_id, VOICE_PROFILES[DEFAULT_VOICE])
    cache_key = _get_cache_key(speech_text, voice_id)

    # 1. In-memory check
    if cache_key in MEMORY_CACHE:
        return MEMORY_CACHE[cache_key]

    # 2. Disk cache check
    disk_path = os.path.join(CACHE_DIR, f"{cache_key}.mp3")
    if os.path.exists(disk_path):
        try:
            with open(disk_path, "rb") as f:
                b64 = base64.b64encode(f.read()).decode("utf-8")
                MEMORY_CACHE[cache_key] = b64
                return b64
        except Exception as e:
            logger.warning("Failed to read cached voice file: %s", e)

    # 3. Live Neural Synthesis via edge_tts
    try:
        communicate = edge_tts.Communicate(
            text=speech_text,
            voice=profile["voice"],
            rate=profile["rate"],
            pitch=profile["pitch"]
        )

        audio_bytes = bytearray()
        # Enforce 4.5s timeout so synthesis never delays the assistant
        async with asyncio.timeout(4.5):
            async for chunk in communicate.stream():
                if chunk["type"] == "audio":
                    audio_bytes.extend(chunk["data"])

        if not audio_bytes:
            return None

        # Save to disk cache
        try:
            with open(disk_path, "wb") as f:
                f.write(audio_bytes)
        except Exception as e:
            logger.warning("Failed to write to voice cache: %s", e)

        b64 = base64.b64encode(audio_bytes).decode("utf-8")
        if len(MEMORY_CACHE) > 200:
            MEMORY_CACHE.clear()
        MEMORY_CACHE[cache_key] = b64

        logger.info("Synthesized %d bytes neural audio for '%s' using %s", len(audio_bytes), speech_text[:35], profile["name"])
        return b64

    except asyncio.TimeoutError:
        logger.warning("Neural speech synthesis timed out for '%s'", speech_text[:35])
        return None
    except Exception as e:
        logger.error("Neural speech synthesis failed: %s", e)
        return None


def pre_cache_common_phrases():
    """Warm-ups and pre-caches frequent affirmative responses on server startup."""
    common_phrases = [
        "Right away, Sir.",
        "Yes, Sir? How can I assist you?",
        "Good morning, Sir. All systems are fully operational.",
        "Scheduled reminder alert, Sir.",
        "Capturing host PC screenshot now, Sir.",
        "Host PC workstation has been locked, Sir."
    ]

    async def _warmup():
        for phrase in common_phrases:
            for v_id in ["jarvis_classic", "friday"]:
                try:
                    await synthesize_speech_async(phrase, v_id)
                except Exception:
                    pass
        logger.info("Neural voice warm-up complete for %d common phrases.", len(common_phrases))

    asyncio.create_task(_warmup())
