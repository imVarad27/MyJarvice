# Jarvis changes

## 1.3.1 — 2026-10-23 — Android build 7

- Fixed background reminder alert delivery over WebSocket: wrapped sends in safe
  error handling and automatically evicted closed or dropped client sockets to prevent
  event loop and thread pool stalls.
- Hardened token streaming for chat generation: safely handled disconnected clients
  during partial response streaming without crashing background model inference threads.
- Corrected logger format specifier in routine morning briefing weather queries.
- Initialized host server main event loop on startup across host lifespan contexts.

## 1.3.0 — 2026-10-21 — Android build 6

- Added private, offline **Routines** under Today → Your tasks. A routine is a
  reusable checklist; it opens a complete preview and only creates local phone
  tasks after an explicit “Add tasks” confirmation. It never sends, opens apps,
  completes tasks, calls a model, contacts the paired PC, or requests permission.
- Added defensive local routine storage (up to 100 routines with up to 25 steps
  each) and one-transaction task creation after approval. Existing tasks, chats
  and settings are untouched.
- Local backup/restore previews now include routines; restore remains merge-only
  and does not overwrite or delete existing routines. Older backups without the
  optional routines section remain supported.
- Includes the prior workspace refinement: calmer themed surfaces, a scrollable
  searchable conversation drawer, readable inert code panels and compact,
  theme-aware voice controls. These are UI changes, not model-capability claims.

## 1.2.2 — 2026-10-17 — Android build 5

- Refined neutral light/dark/AMOLED surfaces, message hierarchy, proportional user
  bubbles, body line spacing and themed composer focus/primary-action treatment.
- Extracted a scrollable conversation drawer with pinned Settings, title/message
  search and confirmed clear-history behind an options menu. Existing Today,
  Saved, draft and calendar destinations remain intact.
- Added inert fenced-code panels with explicit copy, literal whitespace/markup,
  horizontal scrolling and streaming protection. Whole-reply copy/share still
  uses the original text; no prompts or inference/routing policies were changed.
- Tightened the model selector for narrow screens, added recognizable tool icons
  and wrapping reply metadata/actions. References are design inspiration, not
  copied private chats, proprietary assets or claims of equivalent model quality.

## 1.2.1 — 2026-10-14 — Android build 4

- Simplified empty home to a centered welcome and bottom composer. Today and
  Saved items move to sidebar destinations; stored data and assistant features
  remain intact. Removed the duplicate Saved icon from the chat header.
- Refined voice layout: grouped options, visible route picker, compact expandable
  captions, clear microphone/end labels and a responsive, theme-aware indicator.
  Removed decorative infinite orb animation and redundant nested system insets.
- Fixed the idle microphone control to retry listening explicitly, guarded against
  concurrent capture, speaking, thinking, mute and pause. Unmuting during thinking
  no longer starts capture early. This is not an acoustic-recognition upgrade.

## 1.2.0 — 2026-10-14 — Android build 3

- New **Time Rehearsal** in the private week planner: compare a hypothetical
  30/60/90-minute commitment with preparation/recovery buffers and a same-start
  50% overrun. Shows sampled candidates and an overrun-safe filter without booking.
- The week planner now retains whole free gaps transiently for rehearsal, while
  preserving its existing duration suggestions and read-only permission boundary.
- No AI prompts, cloud calls, task changes, calendar writes, notifications or new
  permissions. Incomplete data disables rehearsals. Real-provider/UI checks are
  still needed; this release does not claim globally unique functionality.

## 1.1.0 — 2026-10-13 — Android build 2

- New offline **What's new** screen in Settings → About Jarvis, with feature
  descriptions, where to find them, actual APK version/build type and an explicit
  copy-version button. No phone identifiers or personal content are copied.
- Central `version.properties` now supplies `MAJOR.MINOR.PATCH` and Android's
  increasing build number. The stale hardcoded About label has been removed.
- This install also brings recent rolling-build work onto the connected phone:
  reviewed reply sharing, contact-name drafting, private calendar week planning
  and passphrase-protected local backups. They are existing features bundled in
  this release, not new implementations today.

Earlier development APKs used `1.0` / build 1 without individual release numbers.
No retrospective semantic versions or release tags are claimed for those builds.
