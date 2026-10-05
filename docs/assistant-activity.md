# Activity: phone and PC actions

Open chat tools → Activity to inspect a searchable timeline. Source chips separate All, Phone and PC; the outcome menu filters completed, prepared and attention-needed records. Times are converted from recorded timezone offsets to the phone's local timezone. A prepared action or approved draft is not a delivery/completion confirmation.

## Phone history

Local model tool calls and phone-side host directives record metadata: capability name, outcome and timestamp. Calls and WhatsApp review flows record waiting-for-approval, cancellation, preparation and failures. Email approval records the phone's approval choice, not a successful server send. Invalid/repeated requests, pause blocks and voice/untrusted-content blocks are logged too.

The phone log does not accept a caller-provided summary, tool argument, prompt, transcript, fact, recipient or retrieved content. Labels and summaries are generated from the known capability/outcome catalog. It keeps the latest 500 records in an AtomicFile in Android's app-private no-backup directory. Records are not uploaded, included in Jarvis exports, or transferred through Android backup. This is a convenience history, not a tamper-proof security ledger or a history of every interaction with Android.

Clearing requires confirmation and affects only phone activity metadata. It does not erase tasks, memories, chats, or PC logs, and future actions continue to be recorded. Corrupt history is reported and preserved; recording never silently overwrites unreadable history. A logging/storage failure cannot turn an already completed tool into a failed action. If storage is unavailable, some events may be missing.

## PC history

PC records are fetched from the paired host when connected and stay separate from phone execution results. Loaded PC rows are held only in memory. Offline/failed refresh notices leave phone history usable and mark older PC results as potentially stale. Changing the PC address or pairing token clears the displayed PC cache. Clear phone log never calls a server deletion endpoint.

The Activity client preserves HTTPS/WSS rather than downgrading to HTTP, rejects credential/query-bearing addresses, puts the pairing token only in its Authorization header, and refuses redirects. Existing plain HTTP LAN/USB addresses remain supported: they do not provide transport encryption. Responses have bounded size/record counts and network resources are closed after each request. This change applies to Activity, not an audit of every networking feature in the app.

## Verification

JVM checks cover retention, reload, corruption preservation, local/PC identity collisions, timezone ordering, filters, payload exclusion, logging failures, clock preparation versus completion, secure URL handling and bounded remote data. Compile-only UI tests cover search, source/outcome filters, large text and clear confirmation without touching a personal store. Run instrumentation only on a dedicated emulator/test device: never run connected Gradle tests or uninstall/clear data on the personal phone.

Manual checks still needed: the timeline at narrow widths/large fonts, opening it while offline, refreshing PC data, a blocked voice action, cancelling a phone approval, and clearing only the phone log. Use synthetic content for screenshots and tests.
