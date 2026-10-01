# Personal writing profile

Settings → Writing style stores an optional default tone, draft length,
contraction and emoji preferences, sign-off, and phrases to avoid. Save applies
the changes; Reset clears only the writing profile. The assistant's existing
conversation personality is separate.

Today → Find the right words starts with the saved tone when enabled. Users can
override the tone, omit the profile for one draft, or explicitly remember a new
tone. Preferences are never inferred by reading previous messages. This phase
implements preference memory, not model training or automatic learning from edits.

Prepare in chat builds an editable prompt. No generation, email or message send
occurs until the user takes the relevant next action. Preferences stay in local
storage, but selecting the profile includes them in that draft's prompt: sending
it in PC mode sends that text to the paired host. Both settings and the draft
helper explain this. Other chat requests do not automatically include this profile.

The pure WritingDraftPrompt builder checks input bounds against the existing
4,000-character composer limit. Tests cover omission, per-draft overrides, literal
string quoting, and maximum size. Quoting free-text preferences is a formatting
measure, not a tool authorization boundary; existing action approvals remain
responsible for authorizing sends.

Device tests use synthetic UI data. WritingProfileStoreTest uses the test APK's
private storage, so save/reset checks do not modify a real user's preferences.
