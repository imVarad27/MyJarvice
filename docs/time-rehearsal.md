# Time Rehearsal

Version 1.2.0 / build 3 adds a private, reversible **what-if lens**, rather than
another chat prompt or an autonomous calendar scheduler.

Open Today → Your calendar → Plan my week. Select 30, 60 or 90 minutes, then open
**Time Rehearsal → Rehearse a commitment**. Choose 0, 10, 20 or 30 minutes for
preparation **and** recovery. Jarvis shows whether the selected length fits and
whether a duration 50% longer still fits **at the same start**, with both buffers.
The overrun-safe filter shows up to three earliest matching candidates.

## What the result means

- This is bounded calendar arithmetic, not a learned forecast of your speed or
  an AI confidence score. A 60-minute commitment is compared with 90 minutes;
  90 minutes is compared with 135. These assumptions are visible.
- A candidate reserves nothing. Preparation begins at the rounded start of a
  free gap; the hypothetical commitment starts after preparation. Recovery must
  fit after both the original duration and, for a resilient candidate, the overrun.
- Samples use one earliest start per qualifying gap, at most three per day across
  seven local days. Counts describe samples, not every possible starting time or
  a guaranteed count of all safe choices. Overrun filtering does not resample.
- Existing 9 am–6 pm windows, local 15-minute rounding, free/busy and UTC all-day
  date handling remain unchanged. Incomplete, malformed or over-limit snapshots
  disable rehearsals, not merely show zero slots.
- Only visible calendars exposed by Android are checked. Tasks, travel time,
  hidden/unsynced calendars and undeclared commitments are not considered.
  Explicitly adding a buffer does not determine actual travel time.
- The check timestamp belongs to the calendar snapshot. Refresh before relying
  on the result; a long-open screen can become stale as time/events change.

## Privacy and boundaries

Reuses the planner's explicit READ_CALENDAR consent and foreground refresh checks.
Calculation runs on a worker dispatcher. Event titles/gaps/results stay in
transient composition state: no storage, backup, logging, model context, clipboard,
network upload, background job or new permission. No provider writes, task edits,
notifications, sharing or automatic action. Closing the planner drops scenarios.
See [Private week planner](calendar-week-planner.md) for provider limitations.

## Research and product direction

Checked October 14, 2026; product offerings may change:

- [Motion auto-scheduling](https://www.usemotion.com/help/time-management/auto-scheduling)
  already places tasks by priority, deadlines and availability.
- [Reclaim planner](https://reclaim.ai/features/planner) already schedules tasks and
  meetings around connected calendar events.
- [Google's Android assistant privacy principles](https://blog.google/security/android-gemini-intelligence-security-privacy/)
  emphasize explicit intent and activity visibility.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
  describe minimum 48 dp interactive targets.

Those references informed a different emphasis for Jarvis: offline rehearsal and
inspectable assumptions before committing, with no automatic booking. They do
**not** prove nobody has built a similar feature, and this release makes no
global novelty or superior-intelligence claim.

## Verification

JVM tests exercise both buffers, exact gap boundaries, fragile versus resilient
same-start options, 90→135-minute scenarios, incomplete/invalid/over-limit data,
all-day and free events, local rounding, candidate bounds and source immutability.
Existing week planner tests guard its original suggestions and DST behavior.
Synthetic Compose tests cover explicit expansion, incomplete-state disclosure,
the filter and narrow large-text controls; compile only unless run on a dedicated
emulator/test device. Never run uninstalling instrumentation on a personal phone.

Manual checks remain: actual calendar consent/revocation/providers, foreground
refresh, 320 dp/large text, light/dark/AMOLED themes and date/time-zone changes.
No personal-device install is implied by a build/push request.
