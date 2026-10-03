# Local backup and restore

Settings → Data & storage creates a user-chosen `.jarvisbackup` ZIP archive.
The archive contains a versioned JSON manifest plus media belonging to saved inbox
items. Export and restore run locally through Android's Storage Access Framework;
Jarvis does not upload the backup.

Included:

- conversation history and cited-source metadata;
- explicit memories and imported document text;
- saved inbox notes, links, photos, voice notes and future reminder times;
- phone-native tasks, due dates and completion state;
- appearance, speech, assistant personality and response-mode preferences;
- the explicit personal writing profile.

Excluded by design:

- PC addresses and pairing tokens;
- voiceprint data and wake-listening state;
- on-device model binaries and device-specific model paths;
- active pause state.

The file is **not encrypted**. The Settings screen warns the user to store it
privately. Restore always shows counts and requires confirmation. It merges missing
items and keeps the newer version of matching conversations; it does not erase data.

Import applies bounded parsing: version validation, duplicate ZIP-entry rejection,
safe entry names, per-media and expanded-archive limits, item-count and text-size
limits, and regenerated app-private media paths. Backup content remains data and is
never executed or automatically added to an AI prompt.

Verification:

- `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`
- manual: create a backup through Settings, inspect the confirmation summary on
  restore, merge it, and confirm conversations/saved items remain available.
- do not run connected instrumentation on a personal phone; update with
  `adb install -r` and use manual checks.
