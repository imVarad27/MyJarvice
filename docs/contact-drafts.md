# Contact-aware drafts

Today → Draft / Find the right words → **Choose from contacts** opens the Android
contact picker. Choosing a contact fills the optional recipient field with its
display name. Edit it, clear it, or type a description instead. Cancelling the
picker keeps the existing name and message. Missing pickers, inaccessible
contacts and blank names show an error without preventing manual drafting.

## Privacy and authority

- `ActivityResultContracts.PickContact` uses an explicit user selection. The new
  feature does not request broad `READ_CONTACTS` access. Jarvis still declares and
  requests that permission elsewhere for its existing calling feature; this
  increment does not change calling behavior.
- The repository accepts only a single `com.android.contacts` contact or lookup
  URI, never a collection, data table, foreign provider, query or fragment. It
  queries only `DISPLAY_NAME_PRIMARY` at that exact URI, off the UI thread, and
  closes the cursor. It does not query phone numbers, email addresses or photos,
  enumerate an address book, persist URI grants, or resolve the name to a number.
- Recipient state is transient, not saved to preferences, saved-instance state,
  Activity, a contact cache or backups. Rotating the draft screen may clear a name;
  type or select it again. Other draft fields keep their existing state behavior.
  Dismissing the sheet cancels its coroutine scope; cancelled results are not
  applied to another draft. A provider query already in progress may still finish.
- Name normalization bounds input and output, collapses whitespace, removes
  control and bidi override/isolate characters, and avoids truncating half an
  emoji. Names retain ordinary multilingual text.
- **Prepare in chat** creates an editable request only. That explicit action
  includes the visible name, message and enabled writing profile. Sending the
  request to generate text can store it in chat history and send it to the PC
  when PC routing is used. Those normal chat records may enter user-made backups.
- Names are quoted as literal recipient data, not instructions. Quoting is not a
  prompt-injection security boundary. Existing action confirmations, not prompt
  text or contact selection, enforce consequential-action authorization.
- There is no automatic sending, email/SMS/WhatsApp integration, new model tool,
  contact learning, background contact monitoring or API cost in this phase.

## Verification

`DraftRecipientNameTest` runs on the JVM and covers multilingual names, whitespace,
empty/control-only input, bounds, emoji truncation and literal prompt formatting.
The existing writing-profile maximum-size test now also exercises quoted recipient
characters against the 4,000-character composer limit.

`DraftContactRepositoryTest` injects synthetic cursors to check exact URI/projection,
rejected URI shapes, absent/blank/multiple rows, errors, no broad fallback and
cursor closure. `DraftComposerSheetTest` uses synthetic callbacks/names to check
explicit selection, editing, clearing, error/cancel preservation, large text,
busy-state controls and preparation without generation or sending. Instrumentation
is compile-checked only; it must not be run on a personal device whose app data
could be removed during test cleanup.

Local checks: `gradlew.bat testDebugUnitTest assembleDebug lintDebug
compileDebugAndroidTestKotlin`. Real-device picker behavior remains unverified.
On a dedicated device, test contacts permission denied, picker cancellation,
selection, a deleted contact, large text/keyboard, light/dark themes, rotation,
sheet dismissal and process recreation. Confirm that no generation or send occurs
before the normal chat/user approvals. Do not export real contact screenshots.

## Platform references

Checked October 11, 2026. This implementation uses the existing single-contact
API supported by the current app SDK; it does not claim Android 17's newer
multi-contact picker support.

- [Android common intents: select a contact](https://developer.android.com/guide/components/intents-common#Contacts)
- [AndroidX PickContact contract](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickContact)
