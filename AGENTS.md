# Jarvis repository guidance

## Preserve user data

- Do not uninstall or clear Jarvis on a personal phone, and do not run
  `connectedDebugAndroidTest` there: test cleanup can remove app-private data.
- Install only when the user asks. Prefer `adb -s SERIAL install -r` and stop on
  signing conflicts rather than uninstalling. Keep model files, chats, settings
  and enrolled voice data intact. Never export private phone content for UI checks.

## Versioned deliveries

- For a requested application update delivery (push or install), maintain semantic
  versioning via root `version.properties`. Compatible fixes increment PATCH,
  compatible new features increment MINOR, incompatible public behavior/data/API
  changes increment MAJOR. Increase Android `versionCode` independently each time.
- Don't bump for every local compilation retry or doc-only edit. Don't reuse a
  delivered version for changed app behavior.
- Update `JarvisReleases.latest` and `CHANGELOG.md` together with the version. Keep
  release notes honest about new features versus previously implemented work.
- Read `docs/versioning.md` for the release workflow. A push/install request grants
  only its stated scope; it does not authorize a new GitHub release, PR, message,
  phone-data reset, or unrelated external action.

## Safe verification

- Use `gradlew.bat testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin`
  and `python -m unittest discover -s server -p 'test_*.py'` as local checks.
- Report compile-only UI tests separately from executed tests and actual device
  checks. Do not claim recognition accuracy or model capability from a UI build.
- Preserve unrelated changes and stage only files belonging to the user's task.
