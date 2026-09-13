# Faster replies and Hey Jarvis

The PC route now streams text, disables hidden reasoning for short replies, and keeps the Ollama model loaded for 30 minutes after use. Ordinary general-knowledge questions skip web search; time-sensitive questions still search. Selected neural voices remain available when spoken replies are enabled; choose Android native speech to avoid network speech synthesis. A cold model start can still take longer. These changes do not make a small local model equivalent to a frontier model.

Configure `JARVIS_MODEL`, `JARVIS_CONTEXT_SIZE`, `JARVIS_MAX_TOKENS`, and `JARVIS_KEEP_ALIVE` in `server/.env`. The defaults target the installed Gemma model and an 8 GB GPU. Use `ollama ps` during inference to check GPU placement. Run `python benchmark_response.py` from `server` to measure first text and completion without using personal data. Keep the PC awake and connected for the PC route.

## Hands-free setup

Open Settings → Hands-free voice → Listen for Hey Jarvis. Grant microphone and notification access. The first setup downloads the official Vosk small English model (about 40 MB, Apache 2.0) from https://alphacephei.com/vosk/models/. Afterwards wake detection is offline. Watch for “Listening for Hey Jarvis”. Say “Hey Jarvis”, pause for the voice screen, then give the command. This is two-stage listening, not capture of a command spoken in the same breath as the wake phrase.

The listener releases the microphone during command capture and pauses while Jarvis speaks. Wait for the ready tone before speaking; it now plays after Android reports the command microphone is ready. The voice screen shows partial transcription and recognition errors. Android is asked to allow three seconds of silence before ending a command, although recognition providers may apply their own timing.

The wake listener stays active when you leave the app, with a persistent notification and a Stop listening action. Background wake can open Jarvis when Android permits it and display-over-other-apps access is granted; otherwise tap the notification. It cannot bypass the lock screen. After reboot/force-stop, open Jarvis again. OEM battery restrictions can stop background services; permit background activity in Android settings if needed. Always-on listening uses battery and memory and is optional. Voice profiles are experimental, not authentication: anyone can wake the app. Existing confirmations still apply to calls and email sending.

The wake detector does not send microphone audio to the server. Subsequent command dictation uses Android's configured speech recognition service and may require a network connection.

## Personal tasks (PC-connected mode)

- `add task buy groceries` saves a task in the PC's existing Jarvis database.
- `show my tasks` shows up to 30 open tasks and their IDs.
- `complete task 1` completes that exact task.
- `plan my day` shows saved open tasks and the existing reminders summary.

Tasks survive server restarts. These commands do not create calendar events or send notifications by themselves; use the existing reminder commands for timed alerts. Phone-only mode does not share this PC task database.

## Checks

Do not run Gradle connected-device instrumentation on a personal phone without a verified backup. The test runner used here uninstalled the app during cleanup, removing app-private data. Use an emulator for instrumentation. For a personal phone, use `adb install -r` for updates and manual UI checks; preserve app data and never uninstall to update.

From `server`: `python -m unittest test_assistant_runtime -v`.

From the project root: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.

On a phone, verify wake detection, mic handoff, spoken reply, return to listening after exiting voice mode, background wake, notification stop, and app reopen. Acoustic accuracy and battery behavior require testing on the actual phone in its usual environment. The real-device test confirmed wake detection but initially captured an incomplete question; the subsequent microphone lifecycle and live-transcription revision still needs a complete spoken-turn retest.
