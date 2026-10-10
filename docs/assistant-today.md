# Today and assistant shortcuts

This increment follows the Android-first, near-zero-cost plan: a useful home
screen should open without waiting for a model or a connected PC.

## Shipped behavior

- New conversations open Today: local saved reminders, the next scheduled item,
  two recent conversations, and three recently saved items.
- Today includes phone-native tasks with optional due dates, overdue/today
  grouping, direct completion, editing, deletion, and a completed-items view.
  Tasks are local and work without a model or PC connection.
- Tap a reminder or saved card to open that specific inbox item. The existing
  inbox provides reminder scheduling, rescheduling and deletion.
- Capture a note or link from Today. Saving is local and does not invoke a model.
- Optionally connect the Android calendar with read-only permission. Today's event
  instances are queried on the phone; tapping an event opens the system calendar.
- **Plan my week** opens the next seven local days, conflict markers and selected-
  duration opening suggestions. It is read-only calendar arithmetic, not a model
  prompt, event creator, notification scheduler or complete availability guarantee.
  See [Private week planner](calendar-week-planner.md) for limits and verification.
- The writing helper prepares a chat prompt with recipient, intent and tone.
  The user reviews the prompt before requesting generation. It never sends an
  email or a message itself.
- The attachment/tools sheet supports search and a "Works without PC" filter.
  Command shortcuts populate the composer for review. Attachment and file
  tools keep their explicit picker/navigation flows.
- Pause is visible in chat. Send and microphone controls are disabled while
  paused; incoming PC phone actions and automatic reply playback are suppressed.
  Active dictation stops when the pause preference changes.

## Boundaries

Today reflects the phone's saved inbox and, after explicit permission, today's
read-only calendar events. It does not claim access to notifications or PC task data.
Phone tasks are stored separately from the PC task database. A task due date is an
organizing date, not an alarm or notification.
Calendar text is not added to model prompts. Delivered reminders are cleared by the existing
reminder receiver and therefore are not a completed-task history. The brief
refreshes on foreground entry, on closing the inbox, after capture, and each minute
while its composition is active. No API subscription or new permission is added.

Pause does not revoke credentials or undo requests already accepted by the PC.
Phone speech recognition retains its existing provider behavior and may use the
network; the offline filter refers to whether a shortcut requires the paired PC.

## Verification

- `gradlew.bat testDebugUnitTest assembleDebug`
- `gradlew.bat compileDebugAndroidTestKotlin`
- With a dedicated Android test device: `gradlew.bat connectedDebugAndroidTest`

The instrumented tests use synthetic data and do not read the personal inbox.
Manual device checks: light/dark themes, 320dp width and large font, keyboard in
the draft sheet, returning from an inbox item, quick capture with PC disconnected,
calendar permission grant/deny and an all-day event, and pausing during voice
playback. Keep personal content out of exported test screenshots.
