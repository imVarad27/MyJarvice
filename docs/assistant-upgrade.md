# Faster replies and Hey Jarvis

The PC route now streams text, disables hidden reasoning for short replies, and keeps the Ollama model loaded for 30 minutes after use. Ordinary general-knowledge questions skip web search; time-sensitive questions still search. Selected neural voices remain available when spoken replies are enabled; choose Android native speech to avoid network speech synthesis. A cold model start can still take longer. These changes do not make a small local model equivalent to a frontier model.

Configure `JARVIS_MODEL`, `JARVIS_CONTEXT_SIZE`, `JARVIS_MAX_TOKENS`, and `JARVIS_KEEP_ALIVE` in `server/.env`. The defaults target the installed Gemma model and an 8 GB GPU. Use `ollama ps` during inference to check GPU placement. Run `python benchmark_response.py` from `server` to measure first text and completion without using personal data. Keep the PC awake and connected for the PC route.

## Hands-free setup

Open Settings → Hands-free voice → Set up voice profile first, then enable Listen for Hey Jarvis. Grant microphone and notification access. The first setup downloads the official Vosk small English model (about 40 MB, Apache 2.0) from https://alphacephei.com/vosk/models/. Afterwards wake detection is offline. Watch for “Listening for Hey Jarvis”. Say exactly “Hey Jarvis”, pause for the popup and ready tone, then give the command. “Hi Jarvis”, “Okay Jarvis”, mentions inside sentences, and same-breath commands are rejected. Completed word recognition and an enrolled acoustic match are both required; partial transcripts cannot activate the popup.

The listener releases the microphone during command capture and pauses while Jarvis speaks. Wait for the ready tone before speaking; it now plays after Android reports the command microphone is ready. The voice screen shows partial transcription and recognition errors. Android is asked to allow three seconds of silence before ending a command, although recognition providers may apply their own timing.

The wake listener stays active when you leave the app, with a persistent notification and a Stop listening action. Background wake can open Jarvis when Android permits it and display-over-other-apps access is granted; otherwise tap the notification. It cannot bypass the lock screen. After reboot/force-stop, open Jarvis again. OEM battery restrictions can stop background services; permit background activity in Android settings if needed. Always-on listening uses battery and memory and is optional.

The wake detector does not send microphone audio to the server. Subsequent command dictation uses Android's configured speech recognition service and may require a network connection.

## Personal voice profile

Open **Settings → Hands-free voice → Set up voice profile** and record “Hey Jarvis” three times in a quiet place. Each sample must pass phrase recognition and match the other samples. The raw recordings are used in memory during setup and discarded; Jarvis stores only an app-private acoustic profile. Automatic wake always requires voice protection and a valid profile. Disabling protection or deleting the profile stops the listener. Existing profiles require re-enrollment for the new phrase-validated, silence-trimmed pipeline; the old vector is retained until replaced or explicitly deleted.

This lightweight acoustic matcher can reject the owner or accept a similar voice or recording. It is not secure speaker authentication and cannot guarantee owner-only activation. Keep the phone lock enabled and test in your actual environment.

## Assistant popup and appearance

On an unlocked phone, a verified wake opens a compact translucent Jarvis card rather than replacing the current screen with full chat. Background display requires Android's display-over-other-apps permission; otherwise use the notification. A notification or Settings → Preview Jarvis popup opens an unverified manual session, never a fabricated owner match. Expand transfers the saved conversation to full chat. Typing pauses dictation, and dismissing the popup releases its microphone ownership.

Both composers offer Auto, Phone model, and PC model. The picker explains readiness and connection requirements and saves the route as the default; it does not download a model or start a disconnected server. Appearance offers Pixel-inspired calm surfaces or Jarvis cyan/amber accents, alongside the existing light/dark/AMOLED settings.

If a manually opened voice session is not verified, Jarvis still answers ordinary questions but refuses calls, messages, memory changes, reminder changes, and sensitive PC commands. The user can type the action or start a new verified wake session. This is a convenience and privacy safeguard, not strong biometric authentication; Android screen lock and system biometrics remain the security boundary.

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

On a phone, verify wake detection, mic handoff, spoken reply, return to listening after exiting voice mode, background wake, notification stop, and app reopen. Acoustic accuracy and battery behavior require testing on the actual phone in its usual environment.

2026-09-16: debug build, 23 JVM tests, compile-only instrumentation and lint completed successfully (lint warnings remain, no errors). Updated the connected RMX2061 with `adb install -r`; chat file metadata remained unchanged during the update and blank UI checks. Manually checked the approved blank popup, model picker, Phone → Auto selection, keyboard accessibility and dismissal. The user confirmed the new enrollment completed and the wake popup captured the spoken question. Different-speaker rejection, recordings, noisy environments, locked/background behaviour and sustained battery usage have not been established by this test.
