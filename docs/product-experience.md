# Jarvis: a calm, useful personal assistant

## Reference principles

These are interaction references, not pixel-for-pixel copies or claims that Jarvis has equivalent model capabilities. Product features vary by plan, platform and rollout.

- [ChatGPT](https://help.openai.com/en/articles/6825453-release-notes): simplified chat navigation, a central composer, discoverable tools and continuity between conversations.
- [Gemini](https://blog.google/products-and-platforms/products/gemini/gemini-personalization/): personal context with explicit permission and control over the information used.
- [Claude](https://claude.com/product/overview): a workspace for readable answers and useful outputs. [Artifacts](https://support.anthropic.com/en/articles/9487310-what-are-artifacts-and-how-do-i-use-them) keep reusable work accessible rather than buried in chat.
- [Grok](https://x.ai/grok): direct entry to chat, search and voice, with cited web results.

## Implemented UI direction

Quiet neutral surfaces, a muted teal accent, consistent typography, and fewer competing labels. Chat remains the primary surface, not an overloaded dashboard.

- Tap **Jarvis** to choose Auto, On this phone, or Your PC. The picker explains privacy, readiness and connection requirements.
- The empty chat offers planning, explanation, writing and local-memory prompts. The planning shortcut uses real saved tasks/reminders only when the PC route is available.
- The composer has one attachment entry, dictation, and a primary send/voice action. It no longer reserves an empty supporting-text row.
- The tool sheet groups attachments, personal-assistant shortcuts and PC tools. PC-dependent shortcuts are disabled without the appropriate connection/mode.
- Conversation search matches titles and message text locally. Saved items retain their existing search and reminder controls.
- Replies have a wider reading column, selectable basic bold/code/headings/bullets, and no animated avatar. Streaming hides premature action buttons and redundant thinking indicators.
- Settings start as compact categories with explanatory subtitles. Voice respects the app theme and uses readable status and transcription text.

## What a personal assistant needs next

1. **Data safety:** explicit export/import and verified backups for conversations, memories, saved items and settings. This is more important than cosmetic features. Never run uninstalling instrumentation on a personal phone.
2. **A real Today page:** editable tasks, reminder status and due dates, with a clear distinction between phone and PC storage. Do not synthesize a fake calendar or agenda.
3. **Dependable voice:** end-to-end acoustic tests, interruption handling, clear errors, and transparent Android background/lock-screen limits. UI tests alone do not establish recognition accuracy.
4. **Permissioned integrations:** calendar and contacts, followed by messaging/email where useful. Show a preview and require confirmation for consequential actions. Do not claim integrations until connected and tested.
5. **Personal memory controls:** inspect, edit, forget, and choose what can be shared with the PC. Avoid inferred sensitive memories and hidden personalization.
6. **Connection onboarding:** QR pairing, health diagnostics and a stable discoverable host identity; manual IP addresses belong in advanced settings.

Later candidates: project spaces, an Android widget, and reusable routines. Image/video generation, social feeds and elaborate animations are not priorities for Jarvis's core personal-assistant use case.

## Verification

Build, JVM tests, lint, and compile-only instrumentation are safe local checks. Inspect approved blank-chat/Settings screenshots on the phone; update with `adb install -r` only. Exercise large text, keyboard visibility, mode selection and disabled PC tools. Do not run `connectedDebugAndroidTest` on the personal device: its cleanup previously removed app-private data.
