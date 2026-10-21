# Chat-first home and voice polish

1.2.1 / build 4 responds to the request for a simpler ChatGPT-inspired experience.
The interaction pattern is adapted to native Material 3 and Jarvis themes, not
a pixel-perfect reproduction, copied assets or a claim of equivalent voice/AI.

## Home

- Empty chat displays only a centered welcome. The composer still has attachments,
  dictation, voice and visible Auto/Phone/PC selection; active chat is unchanged.
- Header keeps history, Jarvis/route and new conversation; Saved moves to the drawer.
- Sidebar → Today opens the existing full dashboard in a full-screen dialog.
  Sidebar → Saved items opens the existing inbox. Tasks, reminders, draft helper,
  calendar and rehearsal remain available. No files, preferences or chats are removed.
- Blank home no longer composes the dashboard or triggers its provider reads and
  periodic refresh. Dashboard consent/read/write safeguards are unchanged.
- Closing Today returns to the current chat/draft. Selecting a saved item, existing
  chat, draft or tool follows the existing flow without auto-sending a prompt.

## Voice

- A responsive, theme-aware circle/waveform replaces the glossy drifting orb.
  Bars respond only to reported microphone level while actually listening.
  Speaking/thinking have static indicators, not fake audio amplitude. No infinite
  animation is used; invalid mic levels are bounded/rejected.
- The header exposes route selection and one options menu for session information,
  voice settings and transcript sharing. Existing explicit sharing behavior is
  unchanged; opening a menu never shares automatically.
- The center announces status accessibly; ongoing thinking/speaking is visible
  even when the mic is muted. Provider status remains verbatim, not phrase-matched.
  Captions are compact with an explicit full-text expansion and a scrollable center.
- Labeled 64 dp mic/end controls remain outside the scroll area. Idle/unmuted
  sessions explicitly retry listening rather than toggling mute. The retry checks
  active voice, listening, speaking, thinking, mute and assistant pause. Existing
  microphone permissions and wake/voice verification are not bypassed or reset.
- System insets come from MainScreen's existing parent, avoiding double padding.
  Voice setup/recognizer/model behavior are otherwise unchanged; this work does
  not establish acoustic accuracy, duplex speech or ChatGPT voice parity.

## Reference and verification

OpenAI Docs skill used to consult official material, checked October 14, 2026:
[UI guidelines](https://developers.openai.com/plugins/concepts/ui-guidelines)
describe concise, conversational UI with clear actions. [ChatGPT Voice](https://learn.chatgpt.com/docs/features/voice)
describes voice conversation controls, but concerns the desktop product and does
not establish exact Android styling. The minimal mobile layout here is a design
adaptation, not an assertion about a current mobile screenshot.

JVM tests cover display priority, retry affordances and bounded mic levels.
Synthetic Compose tests cover blank home, sidebar links, mic retry versus mute,
explicit options sharing and narrow large-text voice controls. Compile-only tests
are not device/visual verification. Actual navigation, provider consent, mic
permission/retry, light/dark/AMOLED, keyboard, landscape and large text still need
checks on a dedicated device or an explicitly authorized personal-phone update.
Do not uninstall, clear data or run connected instrumentation on a personal phone.
