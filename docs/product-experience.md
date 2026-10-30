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
- The empty chat is a simple welcome with no prompt tiles or dashboard. Planning and other shortcuts remain behind the composer attachment/tools entry and the sidebar Today destination.
- The composer has one attachment entry, dictation, and a primary send/voice action. It no longer reserves an empty supporting-text row.
- The tool sheet groups attachments, personal-assistant shortcuts and PC tools. PC-dependent shortcuts are disabled without the appropriate connection/mode.
- Conversation search matches titles and message text locally. Saved items retain their existing search and reminder controls.
- Replies have a wider reading column, selectable basic bold/code/headings/bullets, and no animated avatar. Streaming hides premature action buttons and redundant thinking indicators.
- Settings start as compact categories with explanatory subtitles. Voice respects the app theme and uses readable status and transcription text.
- Appearance offers Pixel-inspired or original Jarvis accents. Verified “Hey Jarvis” opens a compact assistant popup on an unlocked phone, with typing, voice, route selection, dismiss and expand controls. Preview is explicitly manual and unverified; it does not bypass voice protection.
- Data & storage can create and merge a local backup of conversations, explicit memories/documents, tasks, routines, saved inbox media, and safe preferences. Optional passphrase protection defaults on on Android 8+; older unprotected backups remain readable. Credentials, voiceprints, wake state, Activity and model files are excluded. Restore requires approval of a validated snapshot; the passphrase is never saved.
- Today includes editable phone-native tasks with due dates, optional local reminders, overdue state, recurring task rules (daily, weekdays, weekly, monthly) with automatic next-occurrence generation upon completion, direct completion and offline storage. Phone and PC tasks are labeled separately, and phone tasks are included in local backups.
- Today → Your tasks → **Routines** provides user-authored reusable checklists. Every run exposes all steps and needs a distinct Add tasks confirmation before its steps enter the phone task list. Routines do not call a model, create calendar events, send data, trigger apps or run in the background; they are private local data included in reviewed backup/restore.
- Today → Your tasks → **Focus** lets a user choose one open phone task and a 25/45/60 minute foreground timer. Pause, end and complete remain direct user choices; dismissing it changes nothing. The timer makes no hidden model/PC call, notification, alarm, app-control request or phone sound-mode change, and does not claim background reliability.
- Activity is a full-screen, searchable phone/PC timeline with readable outcomes and timestamps. Phone tool/action metadata works offline, stays outside backups and can be cleared separately after confirmation; prompts and tool arguments are never stored in the phone log.
- Memory & documents now has a full-screen searchable library, saved-text previews, editable facts, confirmed removal and per-item exclusion from future phone-model tool retrieval. Excluded content stays local and remains in user-initiated backups; there is no automatic PC sharing.
- PC connection checks are available in Settings and the chat connection dialog. Read-only diagnostics separate host pairing, Ollama reachability, model installation and loaded state, with actionable guidance. No prompts are sent and models are not started by checking.
- Today → Your calendar → Plan my week adds a private seven-day agenda, busy-event conflict markers and 30/60/90-minute opening suggestions. It reads Android calendar instances with consent, stays outside model prompts/backups, and never reserves or edits events. Incomplete provider data disables suggestions; real provider/device behavior still needs verification.
- Today → Draft adds an explicit Android contact picker for the recipient name, with editing, clearing and manual fallback. Only the selected display name is queried; no address-book import, number lookup, automatic generation or sending. The name enters the editable chat request only after Prepare; PC routing disclosure is shown. Real-device picker behavior still needs verification.
- Completed text replies offer Review & share in chat and the popup: edit a local copy, review an exact-text snapshot and approve opening Android's share sheet. No recipient, attachment or conversation is appended, and no Send button is automated. Activity stores metadata only and marks the handoff as unconfirmed; actual receiving-app/device behavior remains unverified.
- Settings → About Jarvis now shows the generated APK semantic version, Android build number and development/release type, with an offline What's new catalog and an explicit copy-version action. Central version properties, tests and the changelog keep future delivered updates identifiable. There is no automatic update download or telemetry.
- The private week planner offers **Time Rehearsal**: try a selected commitment length with preparation/recovery buffers, compare a same-start 50% overrun and filter sampled candidates. It is local what-if arithmetic, not an AI forecast or booking. Incomplete data disables results; see [Time Rehearsal](time-rehearsal.md).
- New-chat home is now a quiet welcome and composer with no dashboard tiles. Today (tasks, calendar, rehearsal, capture and drafts) and Saved items are explicit sidebar destinations, so no features/data are removed and blank home no longer queries the calendar/inbox/task dashboard. Voice uses a responsive themed indicator, grouped options, visible route picker, expandable captions and labeled Listen/Mute/Unmute/End controls. See [Chat-first UI](chat-first-ui.md).
- The conversation workspace now uses neutral surfaces, proportional user bubbles, 16/26 sp answer text and inert fenced-code panels with explicit copy. The sidebar/search scroll without losing Settings; history clearing keeps its confirmation. The composer uses restrained focus/primary-action treatment and a width-aware model selector; shortcut icons describe each tool. See [Workspace UI refactor](workspace-ui-refactor.md).
- Today includes a home-screen widget showing open tasks and due dates at a glance, with one-tap shortcuts for hands-free voice, quick note capture, and Today navigation.
- PC connection includes setup code import: paste a `jarvis-pair://v1` URL, JSON config, or `.env` snippet with live host reachability pre-testing and masked token confirmation before applying. The companion `python -m server.pair_helper` CLI utility exposes local addresses and pairing codes directly on the PC.
- Data & storage now tracks and displays the latest backup timestamp so users can verify backup currency at a glance.

## What a personal assistant needs next

1. **Data safety follow-through:** run a real protected and legacy create/restore drill on an unlocked test device and benchmark password derivation/maximum-size archives, then consider opt-in backup reminders. Passphrase protection is implemented, not yet device-validated. Never run uninstalling instrumentation on a personal phone.
2. **Today follow-through:** add optional task notifications and recurring tasks only after the basic local workflow is tested on-device. Keep phone and PC storage visibly distinct.
3. **Dependable voice:** end-to-end acoustic tests, interruption handling, clear errors, and transparent Android background/lock-screen limits. UI tests alone do not establish recognition accuracy.
4. **Permissioned integrations:** validate today's calendar, the read-only week planner and contact-name draft picker against actual providers, plus the new generic reviewed text handoff in receiving apps. Then consider dedicated messaging/email integration where useful. Show a preview and require confirmation for consequential actions. Do not claim live integrations until connected and tested.
5. **Personal memory follow-through:** validate inspect/edit/exclude/remove on-device, then consider explicitly reviewed PC sharing. Avoid inferred sensitive memories and hidden personalization.
6. **Connection onboarding follow-through:** validate the shipped health checks on-device, then add explicitly reviewed QR pairing and a stable discoverable host identity. Diagnostics do not fix DHCP changes or establish USB forwarding themselves.

Later candidates: project spaces. Image/video generation, social feeds and elaborate animations are not priorities for Jarvis's core personal-assistant use case.

## Verification

Build, JVM tests, lint, and compile-only instrumentation are safe local checks. Inspect approved blank-chat/Settings screenshots on the phone; update with `adb install -r` only. Exercise large text, keyboard visibility, mode selection and disabled PC tools. Do not run `connectedDebugAndroidTest` on the personal device: its cleanup previously removed app-private data.
