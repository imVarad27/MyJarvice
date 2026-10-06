# Personal memory controls

Settings → AI & personal knowledge → Manage library opens a full-screen private library. Search matches names and saved text; All, Memories, Documents and Excluded filters keep the collection manageable. Open an item to inspect its saved content. Long documents are displayed in selectable chunks rather than one enormous text layout.

Add an explicit fact (1–300 characters), edit an existing fact, or import the existing text-based PDF/TXT/Markdown formats. Imported documents are preview-only: their extracted text is not edited as a personal memory. Removal requires a reviewed confirmation and never deletes original files, previous chats or existing backups. Edits preserve IDs and the item's retrieval setting; stale edits/removals are rejected rather than silently overwriting a changed item.

The “Use in phone-model tools” switch controls future local library search and memory-list tool results. Excluded entries stay stored and inspectable, count toward the existing 50-memory/20-document limits, and remain in user-initiated backups. Re-saving an identical excluded fact does not silently enable it. Nothing in this phase sends the library to the PC or infers new memories from conversations. Enabled means available to tools, not automatic personalization in every response.

Exclusion is not encryption, deletion or immediate revocation of an in-progress response. Earlier tool observations, existing chat history and manually attached/copied text may still contain that information. Start a fresh conversation for a clean context; deletion of historical chats/backups remains a separate user action. Existing device/voice approvals and untrusted-content guards are unchanged.

Storage stays at the existing app-private `local-knowledge.json` path, using bounded reads and atomic writes. Legacy entries default to enabled, matching previous behavior. Invalid/corrupt storage is reported without silently replacing it. The UI does disk/PDF work off the main thread and refreshes on reopening/foreground entry.

New backups use format version 2 and preserve per-item exclusion. This app still reads version 1; old entries lacking the setting retain their previous enabled behavior. Earlier Jarvis builds refuse version 2 instead of restoring it while ignoring exclusion. Restore is still merge-only: existing IDs and current exclusion choices win over backup copies. Backups remain unencrypted and contain excluded content.

## Verification

JVM tests exercise legacy migration, persistence/backup codec, permission flags, retrieval exclusion, filters, edit bounds, stale edit/delete guards and oversized data. Synthetic instrumentation tests cover the Android store and UI (search, exclusion, editing, confirmation, large text). Compile them locally; execute only on a dedicated emulator/test device, never on the personal phone.

Manual checks remain required on the target phone: narrow width/light/dark/large fonts, keyboard editing, importing a document, corrupted-storage error display, future local tool retrieval after exclusion, and a real version-1/version-2 backup restore drill. Update personal devices only with `adb install -r`; never uninstall or clear app data.
