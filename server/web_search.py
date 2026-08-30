"""
JARVIS 1.0 - Autonomous Web Search & Real-Time Grounding Module
==============================================================
Provides zero-API-key, high-speed live web intelligence:
1. Live Global Weather (Open-Meteo API + Geocoding)
2. Real-Time News Headlines & Summaries (Google News Live RSS)
3. Financial & Cryptocurrency Rates (CoinGecko API)
4. Authoritative Encyclopedia & Concept Search (Wikipedia REST API)
5. General Web Knowledge Search & Source Aggregation
"""

import urllib.request
import urllib.parse
import json
import re
import html
import logging
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger("JarvisWebSearch")

# Weather code translations for human-friendly spoken descriptions
WEATHER_CODE_MAP = {
    0: "clear sky",
    1: "mainly clear",
    2: "partly cloudy",
    3: "overcast",
    45: "foggy",
    48: "depositing rime fog",
    51: "light drizzle",
    53: "moderate drizzle",
    55: "dense drizzle",
    61: "slight rain",
    63: "moderate rain",
    65: "heavy rain",
    71: "slight snow fall",
    73: "moderate snow fall",
    75: "heavy snow fall",
    80: "slight rain showers",
    81: "moderate rain showers",
    82: "violent rain showers",
    95: "thunderstorm",
    96: "thunderstorm with slight hail",
    99: "thunderstorm with heavy hail",
}

# Crypto alias mappings
CRYPTO_ALIASES = {
    "btc": "bitcoin",
    "bitcoin": "bitcoin",
    "eth": "ethereum",
    "ethereum": "ethereum",
    "sol": "solana",
    "solana": "solana",
    "xrp": "ripple",
    "ripple": "ripple",
    "doge": "dogecoin",
    "dogecoin": "dogecoin",
    "ada": "cardano",
    "cardano": "cardano",
    "bnb": "binancecoin",
    "binance": "binancecoin"
}


class WebSearchResponse:
    def __init__(self, evidence_text: str, sources: List[Dict[str, str]]):
        self.evidence_text = evidence_text
        self.sources = sources  # List of {"title": str, "url": str, "domain": str}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "evidence": self.evidence_text,
            "sources": self.sources
        }


def _extract_domain(url: str) -> str:
    """Extracts clean hostname domain for display chips (e.g. bbc.com, wikipedia.org)."""
    try:
        parsed = urllib.parse.urlparse(url)
        domain = parsed.netloc.lower()
        if domain.startswith("www."):
            domain = domain[4:]
        return domain or "web"
    except Exception:
        return "web"


# ==============================================================================
# 1. Live Weather Engine
# ==============================================================================

def get_live_weather(query: str) -> Optional[WebSearchResponse]:
    """Fetches real-time weather, temperature, humidity, and condition."""
    # Smart city extraction: look for 'in <city>', 'for <city>', 'at <city>'
    city = None
    match = re.search(r'\b(?:in|for|at)\s+([A-Za-z\s]+?)(?:\s+(?:today|tomorrow|now|right now|\?|\.|$)|$)', query, flags=re.IGNORECASE)
    if match:
        city = match.group(1).strip()

    if not city:
        # Fallback strip conversational words
        cleaned = re.sub(
            r"\b(jarvis|hey|what|is|the|live|current|weather|like|in|for|at|today|tomorrow|now|right|temperature|forecast|tell|me|how|outside|check)\b",
            "",
            query,
            flags=re.IGNORECASE
        ).strip()
        cleaned = cleaned.rstrip("?.!, ").strip()
        city = cleaned if cleaned else "Pune"

    try:

        # Step 1: Geocoding lookup
        geo_url = f"https://geocoding-api.open-meteo.com/v1/search?name={urllib.parse.quote(city)}&count=1&language=en&format=json"
        req = urllib.request.Request(geo_url, headers={"User-Agent": "JarvisAssistant/1.0"})
        with urllib.request.urlopen(req, timeout=5) as res:
            geo_data = json.loads(res.read().decode("utf-8"))
            if not geo_data.get("results"):
                return None
            loc = geo_data["results"][0]
            lat, lon = loc["latitude"], loc["longitude"]
            city_name = loc["name"]
            country = loc.get("country", "")

        # Step 2: Forecast query
        forecast_url = (
            f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
            f"&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto"
        )
        req2 = urllib.request.Request(forecast_url, headers={"User-Agent": "JarvisAssistant/1.0"})
        with urllib.request.urlopen(req2, timeout=5) as res2:
            weather_data = json.loads(res2.read().decode("utf-8"))
            current = weather_data.get("current", {})
            temp = current.get("temperature_2m", "N/A")
            feel = current.get("apparent_temperature", temp)
            humidity = current.get("relative_humidity_2m", "N/A")
            wind = current.get("wind_speed_10m", "N/A")
            w_code = current.get("weather_code", 0)
            condition = WEATHER_CODE_MAP.get(w_code, "partly cloudy")

            evidence = (
                f"Live Weather Report for {city_name}, {country}:\n"
                f"- Current Temperature: {temp}°C (Feels like {feel}°C)\n"
                f"- Condition: {condition.capitalize()}\n"
                f"- Relative Humidity: {humidity}%\n"
                f"- Wind Speed: {wind} km/h\n"
            )

            sources = [{
                "title": f"Open-Meteo Weather: {city_name}",
                "url": f"https://open-meteo.com/en/docs?latitude={lat}&longitude={lon}",
                "domain": "open-meteo.com"
            }]

            return WebSearchResponse(evidence, sources)
    except Exception as e:
        logger.warning(f"Weather lookup failed: {e}")
        return None


# ==============================================================================
# 2. Real-Time Financial & Cryptocurrency Ticker
# ==============================================================================

def get_crypto_price(query: str) -> Optional[WebSearchResponse]:
    """Fetches real-time crypto price and 24-hour price change in USD and INR."""
    low = query.lower()
    matched_coin = None
    for k, v in CRYPTO_ALIASES.items():
        if k in low:
            matched_coin = v
            break

    if not matched_coin:
        return None

    try:
        url = f"https://api.coingecko.com/api/v3/simple/price?ids={matched_coin}&vs_currencies=usd,inr&include_24hr_change=true"
        req = urllib.request.Request(url, headers={"User-Agent": "JarvisAssistant/1.0"})
        with urllib.request.urlopen(req, timeout=5) as res:
            data = json.loads(res.read().decode("utf-8"))
            coin_data = data.get(matched_coin, {})
            if not coin_data:
                return None

            usd = coin_data.get("usd", 0)
            inr = coin_data.get("inr", 0)
            usd_change = coin_data.get("usd_24h_change", 0.0)

            change_sign = "+" if usd_change >= 0 else ""
            evidence = (
                f"Live Market Data for {matched_coin.capitalize()} ({matched_coin.upper()}):\n"
                f"- Price in USD: ${usd:,.2f}\n"
                f"- Price in INR: Rs. {inr:,.2f}\n"
                f"- 24h Change: {change_sign}{usd_change:.2f}%\n"
            )


            sources = [{
                "title": f"CoinGecko: {matched_coin.capitalize()} Market Data",
                "url": f"https://www.coingecko.com/en/coins/{matched_coin}",
                "domain": "coingecko.com"
            }]

            return WebSearchResponse(evidence, sources)
    except Exception as e:
        logger.warning(f"Crypto lookup failed: {e}")
        return None


# ==============================================================================
# 3. Live News Search Engine (Google News RSS)
# ==============================================================================

def search_live_news(query: str) -> Optional[WebSearchResponse]:
    """Retrieves breaking headlines, publication timestamps, and source links."""
    # Clean topic query
    topic = re.sub(r"\b(what|is|the|latest|news|on|about|headlines|breaking|today|updates|tell|me)\b", "", query, flags=re.IGNORECASE).strip()
    topic = topic.rstrip("?.!, ").strip()
    if not topic:
        topic = "Technology AI"

    try:
        url = f"https://news.google.com/rss/search?q={urllib.parse.quote(topic)}&hl=en-US&gl=US&ceid=US:en"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
        with urllib.request.urlopen(req, timeout=6) as res:
            xml_content = res.read().decode("utf-8", errors="ignore")
            items = re.findall(
                r'<item>.*?<title>(?P<title>.*?)</title>.*?<link>(?P<link>.*?)</link>.*?<pubDate>(?P<pubDate>.*?)</pubDate>',
                xml_content,
                re.DOTALL
            )

            if not items:
                return None

            evidence_lines = [f"Latest Live News on '{topic}':"]
            sources = []

            for i, (title_raw, link_raw, pub_date) in enumerate(items[:4], start=1):
                clean_title = html.unescape(title_raw).strip()
                clean_link = link_raw.strip()

                # Extract publisher from title format "Headline - Publisher"
                publisher = clean_title.rsplit(" - ", 1)[-1] if " - " in clean_title else "News"
                headline = clean_title.rsplit(" - ", 1)[0] if " - " in clean_title else clean_title

                evidence_lines.append(f"{i}. {headline} (Source: {publisher}, Date: {pub_date})")

                sources.append({
                    "title": headline,
                    "url": clean_link,
                    "domain": _extract_domain(publisher) if "." in publisher else f"{publisher.lower().replace(' ', '')}.com"
                })

            return WebSearchResponse("\n".join(evidence_lines), sources)
    except Exception as e:
        logger.warning(f"News lookup failed: {e}")
        return None


# ==============================================================================
# 4. Authoritative Encyclopedia (Wikipedia REST API)
# ==============================================================================

def get_wikipedia_summary(query: str) -> Optional[WebSearchResponse]:
    """Retrieves authoritative encyclopedia extract and direct link."""
    clean_q = re.sub(r"\b(who|is|what|was|explain|tell|me|about|history|of|meaning|definition)\b", "", query, flags=re.IGNORECASE).strip()
    clean_q = clean_q.rstrip("?.!, ").strip()
    if not clean_q:
        return None

    try:
        # Step 1: Search Wikipedia for closest matching title
        search_url = f"https://en.wikipedia.org/w/api.php?action=opensearch&search={urllib.parse.quote(clean_q)}&limit=1&namespace=0&format=json"
        req = urllib.request.Request(search_url, headers={"User-Agent": "JarvisAssistant/1.0 (contact: jarvis@local.ai)"})
        with urllib.request.urlopen(req, timeout=5) as res:
            data = json.loads(res.read().decode("utf-8"))
            titles = data[1] if len(data) > 1 else []
            if not titles:
                return None
            target_title = titles[0]

        # Step 2: Fetch REST summary
        summary_url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{urllib.parse.quote(target_title)}"
        req2 = urllib.request.Request(summary_url, headers={"User-Agent": "JarvisAssistant/1.0 (contact: jarvis@local.ai)"})
        with urllib.request.urlopen(req2, timeout=5) as res2:
            s_data = json.loads(res2.read().decode("utf-8"))
            extract = s_data.get("extract", "")
            title = s_data.get("title", target_title)
            desc = s_data.get("description", "")
            page_url = s_data.get("content_urls", {}).get("desktop", {}).get("page", f"https://en.wikipedia.org/wiki/{target_title}")

            if not extract:
                return None

            evidence = f"Encyclopedia Information on '{title}' ({desc}):\n{extract}"
            sources = [{
                "title": f"Wikipedia: {title}",
                "url": page_url,
                "domain": "wikipedia.org"
            }]

            return WebSearchResponse(evidence, sources)
    except Exception as e:
        logger.warning(f"Wikipedia lookup failed: {e}")
        return None


# ==============================================================================
# 5. Master Web Search Router
# ==============================================================================

def search_web(query: str) -> Optional[WebSearchResponse]:
    """
    Intelligently classifies user query and routes to the best live information source:
    - Weather requests -> Open-Meteo
    - Crypto / Market rates -> CoinGecko
    - Breaking News / Headlines -> Google News RSS
    - General Concepts / Biographies / Definitions -> Wikipedia + News
    """
    low = query.lower().strip()

    # 1. Weather
    if any(k in low for k in ["weather", "temperature", "forecast", "rain", "humidity", "hot outside", "cold outside", "climate"]):
        res = get_live_weather(query)
        if res:
            return res

    # 2. Crypto & Financials
    if any(k in low for k in ["bitcoin", "btc", "ethereum", "eth", "solana", "crypto", "dogecoin", "xrp", "ripple"]):
        res = get_crypto_price(query)
        if res:
            return res

    # 3. Live News
    if any(k in low for k in ["news", "headline", "headlines", "latest on", "what happened", "breaking news"]):
        res = search_live_news(query)
        if res:
            return res

    # 4. Wikipedia / Knowledge
    wiki_res = get_wikipedia_summary(query)
    if wiki_res:
        return wiki_res

    # 5. General Web Search Fallback (News Search)
    news_res = search_live_news(query)
    if news_res:
        return news_res

    return None
