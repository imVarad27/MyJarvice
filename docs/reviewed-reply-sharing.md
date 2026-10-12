# Review a reply before sharing

Completed assistant text replies now offer **Review & share** in the main chat
and the assistant popup. This opens a private editor, not a model request or a
share intent. Edit the copy, tap **Review sharing**, inspect the exact plain text,
then explicitly choose **Approve & open share sheet**. Android lets you choose
the target app or person. Jarvis does not choose a recipient or invoke a Send
button in the receiving app.

This is a generic text handoff, not a Gmail/SMS/WhatsApp integration. A receiving
app can import, save, upload or act on the shared text according to its own rules.
Check its UI before sending. Jarvis cannot enforce another app's final-send policy
or confirm whether the user selected a target, cancelled the sheet or sent a
message. The editor reports only that the share sheet opened.

## Boundaries

- Available for nonblank `RESPONSE` messages from non-user senders up to 20,000
  UTF-16 characters. Streaming/partial replies, errors, unsupported message types,
  user messages and oversized replies do not offer this action. Existing text
  selection/copy controls remain available.
- Only the displayed raw text copy is prepared. Markdown is not converted, and
  inline URLs or retrieved-source text remain visible for the user to edit.
  Separate citation chips, images, audio, timestamps, sender labels, surrounding
  conversation and contact identifiers are not appended to the outgoing payload.
- The outbound intent is an `ACTION_SEND` with `text/plain` and only `EXTRA_TEXT`,
  wrapped in `Intent.createChooser`. There is no predefined package, component,
  recipient, URI, attachment, file grant, email subject or silent/direct-send path.
- The exact preview text is an immutable, one-use approval snapshot. Changed text,
  cancelled previews, disposal and repeat approval clicks invalidate/reject reuse.
  The final callback consumes the snapshot before it launches the intent.
  This token is UI state, not a persisted credential or model-accessible tool.
- Over-limit edits are rejected with a visible error, not silently truncated.
  Blank or rejected edits cannot enter review. A failed external launch keeps the
  copy available but retry requires another preview and approval.
- Editing is local and offline. It never rewrites the saved reply, invokes a model,
  uses the PC, imports contacts or learns a writing preference. Edited text and
  approval state are not saved, cached or included in backups. Native Back and the
  close button ask before discarding a changed copy. Activity/process recreation
  can clear edits without a prompt; the original remains in chat history.
  The existing assistant popup also closes when another activity fully covers it;
  a handoff may therefore clear its edited copy even if the target is later cancelled.
- Editor, preview and discard windows request Android secure-window protection.
  This is not protection against a compromised device, input method, other physical
  cameras or the receiving app once the text has been shared.
- Activity receives only the static `reply.share` action and outcomes: awaiting
  approval, cancelled, approved, started or failed. No text, destination or contact
  is logged. Writes are queued off the UI thread, independent of closing the editor,
  but remain best-effort if storage fails or the app process is killed. A `started`
  event is explicitly not proof of delivery. The existing transcript-sharing and
  clipboard controls are unchanged by this feature.

## Verification

`ReplyShareApprovalTest` covers eligibility, limits, exact whitespace/Unicode/source
preservation, mismatch, cancellation, single use and concurrent approval attempts.
`ActionTimelineTest` checks the new static metadata round-trip and unconfirmed
handoff label.

Compile-only instrumentation includes `ReplyShareIntentTest` (exact payload and
absence of recipients/attachments/direct targets) and `ReviewReplyDialogTest`
(opening/editing/preview without sharing, cancellation, exact approved edits,
failed-launch retry, blank/oversized input, large text and discard approval).
These tests use synthetic data/callbacks and do not open real external apps.
`ReplyReviewEntryTest` checks that the selected completed reply opens its own text
without copy/speech callbacks, and that streaming/error replies have no review action.

Local checks: `gradlew.bat testDebugUnitTest assembleDebug lintDebug
compileDebugAndroidTestKotlin`; `python -m unittest discover -s server -p 'test_*.py'`.
Real-device share targets, keyboard/insets and popup behavior still need validation
on a dedicated device. Test 320dp width/large text, light/dark themes, native Back,
rotation, missing handlers, cancelled chooser, failure/retry and a receiving app's
draft screen. Do not run uninstalling instrumentation on a personal phone or export
screenshots of private replies. This feature does not establish that the previous
calendar/contact provider integrations have been tested on-device.

## Platform reference

[Android: Send data to other apps](https://developer.android.com/develop/ui/compose/sharing/send)
describes `ACTION_SEND`, exact MIME types and using `Intent.createChooser` for the
system Sharesheet. Checked October 12, 2026. Target-app behavior varies by installed
app, version and device; there is no guarantee of a message draft or final delivery.
