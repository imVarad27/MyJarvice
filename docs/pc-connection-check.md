# PC connection diagnostics

Settings → PC connection → Check connection, or chat → Connect your PC, runs a user-triggered read-only check. It does not save/change the entered connection, send a prompt, read phone content, start Ollama, load/pull a model or change PC settings. Results live only in screen memory and are cleared when the address/token changes. Cancel aborts the phone request; a server probe already received may finish.

The phone calls authenticated `GET /api/health`. Pairing is validated before the host probes Ollama. The response contains a protocol version, server version, configured model and runtime/installation/loaded status—not credentials, model inventory, files, device telemetry or conversations. The host uses [Ollama's model-list endpoint](https://docs.ollama.com/api/tags) to check installation and its [running-model endpoint](https://docs.ollama.com/api/ps) to check loaded state. A missing/unsupported running-model endpoint leaves loaded state unknown rather than inventing a result.

The UI distinguishes an unreachable PC, rejected token, older server without this endpoint, invalid response, stopped runtime, missing model and installed model. Installed-but-not-loaded is not an error: a subsequent real request may load the model and take longer. These probes do not establish WebSocket connectivity, generation quality, GPU placement, sustained uptime or that the next request will succeed.

Android requests have connect/read/overall deadlines (3/6/9 seconds), a 16 KiB response limit, no retries and no redirects. Server model lists are bounded to 256 KiB/500 entries and use two-second socket timeouts; only GET list requests are issued. Polling is explicit, not continuous. Raw provider/server errors are not displayed or stored in diagnostic reports. Authentication is a pairing check, not a cryptographic host-identity pin.

## Address and credential handling

Chat, Activity, diagnostics and file transfer use the same address parser. Bare `pc.local` or an IP defaults to port 8000; explicit HTTP/HTTPS/WS/WSS URLs retain their scheme's default port unless a port is supplied. Chat maps HTTP→WS and HTTPS→WSS while retaining a supplied WebSocket path. REST endpoints use the origin's API paths; arbitrary reverse-proxy path prefixes are not supported. Credentials in URLs, query strings, fragments, backslashes and whitespace-bearing addresses are rejected.

HTTPS/WSS is no longer silently downgraded for file transfer. Chat/Activity/file-transfer/diagnostic clients refuse redirects that could forward credentials or data elsewhere. Certificates are not bypassed. Plain HTTP/WS remains available for existing LAN/USB setups and is unencrypted. Use a trusted network or properly configured HTTPS/WSS; this phase does not automatically install TLS or audit every file-transfer/storage behavior.

New installations start with an empty pairing token. Blank or invalid tokens do not fall back to the old hardcoded credential; saved tokens are retained, including existing manually configured ones. Copy `JARVICE_API_TOKEN` from your own server/.env without publishing it. WebSocket callbacks from superseded connections are ignored so an older socket cannot overwrite a newly connected state. Message/action contents are no longer printed by the WebSocket debug receive logs.

## Practical setup

- Start Ollama and the updated Jarvis host on the PC, then check again. Older hosts may still run chat but return 404 for health.
- Wi-Fi: PC and phone must actually be able to communicate. Guest isolation, VPNs, firewall rules, PC sleep and a changed address can prevent this even with the same Wi-Fi name.
- USB: `127.0.0.1:8000` refers to the phone itself unless the PC has established `adb reverse tcp:8000 tcp:8000`. Choosing “Use USB connection” only fills the address; it doesn't establish forwarding.
- This feature does not discover hosts or fix DHCP/IP changes. Router DHCP reservation or an explicitly configured resolvable hostname can help; QR pairing, discovery and durable host identity remain future work.

## Verification

Python tests verify auth-before-probe, cold/missing/loaded/unknown models, bounded lists, aliases and error privacy with synthetic data. JVM tests verify secure address parsing, token bounds, protocol validation, explicit read-only requests, error categories, redirect refusal and bounded responses. Synthetic Compose tests compile for result labels/cancel controls; they require a dedicated emulator/test device to execute.

Manual tests remain required: phone-to-PC reachability, real runtime stop/start, missing model, invalid token, HTTPS certificate failure, USB forwarding, canceled/changed-address checks, and large-text/narrow-screen layouts. Personal phone updates must use `adb install -r`; do not uninstall, clear data or run connected Gradle instrumentation on it. No sensitive screenshots are needed for this phase.
