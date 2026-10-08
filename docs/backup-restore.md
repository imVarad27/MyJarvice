# Local backup and restore

Settings → Data & storage creates a user-chosen `.jarvisbackup` archive.
Choose the destination, then leave **Passphrase protection** enabled (recommended)
and enter/confirm a passphrase of 12–128 characters. Several unrelated words are
better than a short predictable password. To restore, select the file, unlock it,
review its item counts, then explicitly approve **Merge backup**.

Inside is the existing ZIP with a versioned JSON manifest and saved inbox media.
Protected exports wrap the whole ZIP in authenticated encryption. Export and
restore run locally through Android's Storage Access Framework; Jarvis does not
upload the backup. A provider you choose (for example, Drive) may itself sync the
chosen document; use on-device storage if you want to avoid that.

Included:

- conversation history and cited-source metadata;
- explicit memories and imported document text;
- per-item phone-model retrieval/exclusion settings (excluded text is still included);
- saved inbox notes, links, photos, voice notes and future reminder times;
- phone-native tasks, due dates and completion state;
- appearance, speech, assistant personality and response-mode preferences;
- the explicit personal writing profile.

Excluded by design:

- PC addresses and pairing tokens;
- voiceprint data and wake-listening state;
- on-device model binaries and device-specific model paths;
- active pause state.
- phone Activity log.

Protection is optional and defaults on on Android 8/API 26 or newer. Disabling it
explicitly shows a readable-data warning; those exports remain ordinary ZIPs.
Android 7 devices can still create/read unprotected backups, but cannot use the
new protected format. There is no weaker cryptographic fallback.

Jarvis does not save, back up, send, or log the passphrase. It is transient dialog
state, not saved instance state; editable values are cleared on submit/cancel,
and operation-owned character arrays/key bytes are cleared after use. Password and
review dialogs request Android screenshot protection. This is not a guarantee of
complete erasure from a managed heap, a third-party keyboard, or a compromised
device. A forgotten passphrase **cannot be recovered**. Keep it separately in a
trusted password manager; test restore before relying on the archive.

Restore always shows counts and requires confirmation. It merges missing items
and keeps the newer version of matching conversations; it does not erase data.
Settings and the writing profile may change, as the review warns. The merge uses
the authenticated/validated snapshot you reviewed, not a second read of a mutable
external file. Cancel drops that snapshot; it cannot be reused after merge.
Decryption/authentication and all item decoding happen before any restore writes.
A wrong password, corrupted tag or unsupported envelope stops before merging.
An unexpected storage failure during the multi-store merge is not transactional:
some items may already have been added; no delete/replace-all rollback is attempted.

Canceling after the document picker or a failed write can leave an empty/incomplete
document at your chosen destination. Jarvis does not silently delete provider files.

Import applies bounded parsing: version validation, duplicate ZIP-entry rejection,
safe entry names, per-media and expanded-archive limits, item-count and text-size
limits, safe media item IDs, and regenerated app-private media paths. Backup content remains data and is
never executed or automatically added to an AI prompt.

## Protected envelope v1

The binary header contains `JRVCRYPT` (8 ASCII bytes), envelope version `1` (one
byte), a big-endian fixed iteration count `600000` (4 bytes), a random 16-byte salt,
and a random 12-byte nonce. It is authenticated as GCM additional data. Ciphertext
and its 16-byte tag follow. The key is 256 bits, derived with
PBKDF2-HMAC-SHA256; encryption is AES-256-GCM via the platform JCA provider. Salt
and nonce are freshly generated with `SecureRandom` for each export. Unsupported
versions/work factors are rejected before key derivation to prevent attacker-chosen
expensive KDF parameters. The envelope carries no conversation metadata.

The compressed ZIP is limited to 34 MiB (plus 57 envelope bytes); its expanded
content is still limited to 32 MiB, metadata to 20 MiB and each media item to 12 MiB.
Authentication uses bounded in-memory buffers before ZIP parsing; there is no
unencrypted temporary archive on disk. Peak heap usage is higher than the archive
size, so maximum-size backups still need testing on lower-memory devices. Password
derivation and file processing run off the UI thread, with progress and disabled
controls; phone performance has not yet been benchmarked.

Algorithm references checked 2026-10-08: [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography),
[Android SecretKeyFactory availability](https://developer.android.com/reference/javax/crypto/SecretKeyFactory),
[OWASP authenticated encryption guidance](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html),
and [OWASP PBKDF2 work-factor guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
This is not a claim of a security audit or FIPS certification.

The inner manifest still uses version 2 to preserve knowledge exclusion. Unprotected version-1 and version-2 archives remain readable; entries without an exclusion setting default to their previous enabled behavior. Earlier Jarvis versions reject manifest version 2 and cannot read the new encrypted envelope. Existing entries and their current settings win during merge restore.

Verification:

- `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`
- JVM tests cover real JCA round trips, Unicode/spaces, fresh randomness, wrong
  passwords, salt/nonce/ciphertext/tag tampering, fixed-work-factor/version rejection,
  short reads, truncation/extra bytes, legacy passthrough, size bounds and password policy.
- UI and isolated-store instrumentation tests cover password confirmation,
  explicit unprotected choice, disabled busy controls, snapshot-only merge,
  credential exclusion, media/exclusion preservation and legacy manifest restore.
  They are compile-only unless run separately on an emulator.
- pending manual device drill: create a protected backup through Settings, try a
  wrong password, review/cancel, then review/merge and confirm conversations,
  tasks, excluded memories and saved media remain available. Also test an older
  unprotected backup. Keep a separate known-good backup until this is verified.
- do not run connected instrumentation on a personal phone; update with
  `adb install -r` and use manual checks.
