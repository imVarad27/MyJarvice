"""
JARVIS 1.0 - Autonomous Executive Morning Briefing Synthesizer
==============================================================
Aggregates live weather, host PC telemetry, breaking tech headlines,
and scheduled agenda into an executive spoken status report.
"""

import datetime
import logging
from typing import Dict, Any, List, Optional
import web_search
import pc_controller
import scheduler

logger = logging.getLogger("JarvisBriefing")


def compile_briefing_context(user_name: Optional[str] = None) -> Dict[str, Any]:
    """Compiles multi-source live telemetry and data for the executive briefing."""
    address = user_name if user_name else "Sir"
    now = datetime.datetime.now()

    # 1. Date & Time
    date_str = now.strftime("%A, %d %B %Y")
    time_str = now.strftime("%I:%M %p")

    # 2. Live Weather (Pune / Default)
    weather_info = "Weather telemetry unavailable."
    weather_sources = []
    try:
        w_res = web_search.get_live_weather("Pune")
        if w_res and w_res.evidence_text:
            weather_info = w_res.evidence_text.strip()
            weather_sources = w_res.sources
    except Exception as e:
        logger.warning("Weather fetch in briefing failed: %e", e)

    # 3. Host Workstation Hardware Telemetry
    pc_status = "Host PC telemetry nominal."
    try:
        telemetry = pc_controller.get_system_telemetry()
        cpu = telemetry.get("cpu_percent", 10)
        ram = telemetry.get("ram_percent", 50)
        ram_avail = telemetry.get("ram_available_gb", 8.0)
        battery = telemetry.get("battery_percent")
        plugged = telemetry.get("power_plugged")

        bat_str = f", Battery at {battery}% ({'Charging' if plugged else 'Discharging'})" if battery is not None else ""
        pc_status = f"Host workstation operating with CPU at {cpu}%, RAM at {ram}% ({ram_avail:.1f} GB available){bat_str}."
    except Exception as e:
        logger.warning("PC telemetry fetch in briefing failed: %s", e)

    # 4. Breaking Tech News
    news_info = "No breaking alerts."
    news_sources = []
    try:
        n_res = web_search.search_live_news("technology AI")
        if n_res and n_res.evidence_text:
            # Take top 2 headlines
            lines = n_res.evidence_text.splitlines()[:3]
            news_info = "\n".join(lines)
            news_sources = n_res.sources[:2]
    except Exception as e:
        logger.warning("News fetch in briefing failed: %s", e)

    # 5. Scheduled Agenda / Reminders
    reminders = scheduler.get_active_reminders()
    if reminders:
        agenda_info = f"You have {len(reminders)} scheduled task{'s' if len(reminders) > 1 else ''}:\n"
        for i, r in enumerate(reminders, 1):
            due_dt = datetime.datetime.fromtimestamp(r["due_timestamp"])
            agenda_info += f"- {r['task']} (at {due_dt.strftime('%I:%M %p')})\n"
    else:
        agenda_info = "Your schedule is clear with no pending reminders."

    all_sources = weather_sources + news_sources

    context_prompt = (
        f"EXECUTIVE MORNING BRIEFING TELEMETRY:\n"
        f"- Target User: {address}\n"
        f"- Current Timestamp: {date_str} at {time_str}\n"
        f"- Local Weather Report:\n{weather_info}\n"
        f"- Workstation Status: {pc_status}\n"
        f"- World & Tech Headlines:\n{news_info}\n"
        f"- Daily Agenda & Reminders: {agenda_info}\n\n"
        "INSTRUCTION: Synthesize this into a polished, crisp, spoken Iron Man JARVIS-style executive morning briefing. "
        "Begin with a warm, formal greeting to the user, present the key points smoothly (weather, workstation readiness, top news, and agenda), "
        "and conclude with an affirmative ready-to-assist sign-off."
    )

    return {
        "context_prompt": context_prompt,
        "sources": all_sources
    }
