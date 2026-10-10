# Private week planner

Open Today → Your calendar → **Plan my week**. Jarvis shows today and the next six
local calendar days, overlapping busy events and candidate 30-, 60- or 90-minute
openings. This is deterministic calendar arithmetic, not an AI/model capability.
It works with the calendar data already available on the phone without a PC or API
subscription. The calendar app's own account sync may still use a network.

## Consent and privacy

- Read-only Android `READ_CALENDAR` permission is required; request it explicitly
  from Today or the planner. A denial leaves a useful explanation and a link to
  Android's application permission settings. No new manifest permission is added.
- Jarvis queries `CalendarContract.Instances`, so recurring events are represented
  by their occurrences. Hidden and cancelled instances are omitted.
- Titles, locations and timestamps remain transient screen state. They are not
  sent to phone/PC models, cached, put in backups, logged or used for personalization.
- Tap an event to open that exact occurrence in the system calendar with its original
  begin/end extras. Jarvis does not create, edit, delete, invite or share calendar
  events. It does not add `WRITE_CALENDAR` or perform calendar provider writes.
- Leaving the planner cancels its UI jobs and drops the snapshot. A blocking provider
  read may finish on its worker thread before cancellation takes effect, but its
  result is not published by the cancelled screen.
- Refresh and foreground return re-read permission and calendar data. Permission
  revocation clears the previous snapshot before any subsequent query/display;
  tapping an event also rechecks permission. Android controls the actual permission.

## How openings are calculated

- Window: **9 am–6 pm in the phone's current local time zone**. This is a disclosed
  fixed daytime default, not learned working hours or a guarantee of availability.
- Only remaining time from the query's check timestamp is offered for today. Starts
  round up to a local 15-minute boundary; fractional-offset time zones are supported.
- Busy and tentative events block time. Explicit `AVAILABILITY_FREE` events stay
  visible but do not block; unknown availability is treated conservatively as busy.
- UTC all-day dates are converted to local calendar dates before overlap/gap checks.
  Multi-day/all-day busy instances block every covered local day. A free all-day
  holiday does not block. Query padding includes UTC all-day dates near local boundaries.
- Adjacent events do not conflict. Overlapping busy intervals are merged for gap
  calculations; both overlapping event rows get a text marker (not just color).
- At most three candidate openings per day are shown, one selected-duration slot
  per eligible gap. These are suggestions, not every possible starting time.
- Days use `Calendar` date addition, not fixed 24-hour arithmetic, for DST changes.
- The view is capped at 500 instances and 2,000 scanned rows; title/location text is
  bounded. Truncation or malformed instance times mark the snapshot incomplete and
  **disable all opening suggestions**. A null/failed provider is an error, never a
  fabricated empty/free schedule. Today's existing card uses the same padded,
  local-date-aware read and treats incomplete data as unavailable too.

Only visible calendars that Android exposes are checked. Phone/PC tasks, travel
buffers, working hours, undeclared commitments and unsynced/hidden calendars are
not known. No slot is reserved. The screen says to refresh before relying on an
opening and shows the check time. Read-only arithmetic does not resolve conflicts.

## UI and verification

The planner is a full-screen dialog with themed Material 3 controls, refresh/close,
wrapping duration chips, per-day sections and lazy event rows. Eight instances per
day are shown initially, with an explicit show-more/show-fewer control. Overnight
events have continuation labels. Loading hides stale results and disables refresh.

JVM tests cover overlaps, touching boundaries, free/busy and all-day events, positive/
negative/fractional-offset zones, overnight instances, recurrence/deduplication,
duration selection, past-time exclusion, bounds, incompleteness, spring/fall DST.
Synthetic UI and injected-cursor repository instrumentation tests compile without
reading a personal calendar. Execute those only on an emulator/test device, not
the personal installation (the runner's cleanup may remove app data).

Manual device verification remains necessary: consent grant/deny/revoke, recurring
and all-day instances from the actual calendar provider, refresh after an edit,
calendar intent opening, large text, light/dark themes and midnight/time-zone changes.
Use `adb install -r` for any explicitly requested personal-phone update; never
uninstall or clear data as a test step.

Platform references checked 2026-10-10:
[Calendar provider and read/write permissions](https://developer.android.com/identity/providers/calendar-provider),
[Instances query and occurrence timestamps](https://developer.android.com/reference/android/provider/CalendarContract.Instances),
[UTC all-day dates and availability](https://developer.android.com/reference/android/provider/CalendarContract.Events).

This feature does not add task notifications or recurrence. Those remain gated on
basic local-task testing on a dedicated device, as documented in the roadmap.
