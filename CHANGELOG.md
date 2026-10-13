# Jarvis changes

## 1.1.0 — 2026-10-13 — Android build 2

- New offline **What's new** screen in Settings → About Jarvis, with feature
  descriptions, where to find them, actual APK version/build type and an explicit
  copy-version button. No phone identifiers or personal content are copied.
- Central `version.properties` now supplies `MAJOR.MINOR.PATCH` and Android's
  increasing build number. The stale hardcoded About label has been removed.
- This install also brings recent rolling-build work onto the connected phone:
  reviewed reply sharing, contact-name drafting, private calendar week planning
  and passphrase-protected local backups. They are existing features bundled in
  this release, not new implementations today.

Earlier development APKs used `1.0` / build 1 without individual release numbers.
No retrospective semantic versions or release tags are claimed for those builds.
