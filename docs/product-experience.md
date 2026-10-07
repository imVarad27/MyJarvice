# Jarvis: a calm, useful personal assistant

## Reference principles

These are interaction references, not pixel-for-pixel copies or claims that Jarvis has equivalent model capabilities. Product features vary by plan, platform and rollout.

- [ChatGPT](https://help.openai.com/en/articles/6825453-release-notes): simplified chat navigation, a central composer, discoverable tools and continuity between conversations.
- [Gemini](https://blog.google/products-and-platforms/products/gemini/gemini-personalization/): personal context with explicit permission and control over the information used.
- [Claude](https://claude.com/product/overview): a workspace for readable answers and useful outputs. [Artifacts](https://support.anthropic.com/en/articles/9487310-what-are-artifacts-and-how-do-i-use-them) keep reusable work accessible rather than buried in chat.
- [Grok](https://x.ai/grok): direct entry to chat, search and voice, with cited web results.

## Implemented UI direction

Quiet neutral surfaces, a muted teal accent, consistent typography, and fewer competing labels. Chat remains the primary surface, not an overloaded dashboard.

- Choose Auto, Phone model, or PC model directly inside either composer (or from the chat header). The picker explains privacy, readiness and connection requirements.
- The empty chat offers planning, explanation, writing and local-memory prompts. The planning shortcut uses real saved tasks/reminders only when the PC route is available.
- The composer has one attachment entry, dictation, and a primary send/voice action. It no longer reserves an empty supporting-text row.
- The tool sheet groups attachments, personal-assistant shortcuts and PC tools. PC-dependent shortcuts are disabled without the appropriate connection/mode.
- Conversation search matches titles and message text locally. Saved items retain their existing search and reminder controls.
- Replies have a wider reading column, selectable basic bold/code/headings/bullets, and no animated avatar. Streaming hides premature action buttons and redundant thinking indicators.
- Settings start as compact categories with explanatory subtitles. Voice respects the app theme and uses readable status and transcription text.
- Appearance offers Pixel-inspired or original Jarvis accents. Verified “Hey Jarvis” opens a compact assistant popup on an unlocked phone, with typing, voice, route selection, dismiss and expand controls. Preview is explicitly manual and unverified; it does not bypass voice protection.
- Data & storage can create and merge a local backup of conversations, explicit memories/documents, saved inbox media, and safe preferences. Credentials, voiceprints, wake state, and model files are excluded; restore is reviewed and non-destructive.
- Today includes editable phone-native tasks with due dates, overdue state, direct completion and offline storage. Phone and PC tasks are labeled separately, and phone tasks are included in local backups.
- Activity is a full-screen, searchable phone/PC timeline with readable outcomes and timestamps. Phone tool/action metadata works offline, stays outside backups and can be cleared separately after confirmation; prompts and tool arguments are never stored in the phone log.
- Memory & documents now has a full-screen searchable library, saved-text previews, editable facts, confirmed removal and per-item exclusion from future phone-model tool retrieval. Excluded content stays local and remains in user-initiated backups; there is no automatic PC sharing.
- PC connection checks are available in Settings and the chat connection dialog. Read-only diagnostics separate host pairing, Ollama reachability, model installation and loaded state, with actionable guidance. No prompts are sent and models are not started by checking.

## What a personal assistant needs next

1. **Data safety follow-through:** run a real create/restore drill on an unlocked test device, then consider optional passphrase encryption and backup reminders. Never run uninstalling instrumentation on a personal phone.
2. **Today follow-through:** add optional task notifications and recurring tasks only after the basic local workflow is tested on-device. Keep phone and PC storage visibly distinct.
3. **Dependable voice:** end-to-end acoustic tests, interruption handling, clear errors, and transparent Android background/lock-screen limits. UI tests alone do not establish recognition accuracy.
4. **Permissioned integrations:** calendar and contacts, followed by messaging/email where useful. Show a preview and require confirmation for consequential actions. Do not claim integrations until connected and tested.
5. **Personal memory follow-through:** validate inspect/edit/exclude/remove on-device, then consider explicitly reviewed PC sharing. Avoid inferred sensitive memories and hidden personalization.
6. **Connection onboarding follow-through:** validate the shipped health checks on-device, then add explicitly reviewed QR pairing and a stable discoverable host identity. Diagnostics do not fix DHCP changes or establish USB forwarding themselves.

Later candidates: project spaces, an Android widget, and reusable routines. Image/video generation, social feeds and elaborate animations are not priorities for Jarvis's core personal-assistant use case.

## Verification

Build, JVM tests, lint, and compile-only instrumentation are safe local checks. Inspect approved blank-chat/Settings screenshots on the phone; update with `adb install -r` only. Exercise large text, keyboard visibility, mode selection and disabled PC tools. Do not run `connectedDebugAndroidTest` on the personal device: its cleanup previously removed app-private data.
