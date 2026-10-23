# App versions and release identity

`version.properties` is the APK version source. This release is **1.3.1, build 7**.
The previous rolling build had versionName `1.0`, versionCode 1. No saved-data format
change or account/model reset is introduced by the versioning work.

Gradle validates a stable three-component version without leading zeros and an
Android build number from 1 to 2,100,000,000. Both debug and release builds use these
values; About and What's new read generated `BuildConfig` metadata, not UI literals.
Development/Release is shown separately from semantic version. Neither label is a
claim about model quality, product maturity or completion of all device checks.

## Release rules

Treat public behavior, data interchange, saved-data compatibility and approval
contracts as this application's versioned compatibility surface.

- **PATCH** (`1.1.0 → 1.1.1`): compatible fixes, including UI/voice/connection fixes.
- **MINOR** (`1.1.0 → 1.2.0`): compatible new features. Reset patch to zero.
- **MAJOR** (`1.1.0 → 2.0.0`): incompatible behavior/data/API changes. Reset minor
  and patch; explicitly plan migrations and user-visible compatibility warnings.
- Independently increase **versionCode** for every delivered APK update. It is not
  inferred from a date or the semantic version. Never decrease it or reuse a shipped
  version pair for changed app behavior. Local compilation retries are not releases.
- Doc-only changes don't require a new APK version. Stable semver is currently
  supported; prerelease schemes need explicit handling before introducing them.

For each delivery with application changes:

1. Bump both properties appropriately before the final build/install.
2. Update `JarvisReleases.latest` with the same version, actual release date, concise
   changes and navigation hints. Preserve older records in `CHANGELOG.md`; do not
   relabel prior development work as new functionality.
3. Run JVM tests, APK build, lint and compile-only instrumentation. `AppReleaseTest`
   fails if current notes and generated APK version disagree.
4. Commit/push only with user authorization. After a successful release push, create
   an annotated `vMAJOR.MINOR.PATCH` tag for that commit and push that specific tag
   when the user requests a versioned release; never overwrite an existing tag.
5. Install only when requested, using `adb -s SERIAL install -r APK`. Check the
   installed package's versionName/versionCode and launch result. Do not uninstall,
   clear data, downgrade, or run uninstalling instrumentation on a personal phone.
   Stop and ask if an in-place update cannot be installed because of signing issues.

The in-app catalog is bundled and read-only. It does not check for updates, download
APKs, contact GitHub, prompt a model, request permissions or send telemetry. Copy
app version writes only version/build metadata to the clipboard after a tap. A
catalog/version mismatch shows a warning instead of showing stale notes as current.

## References

Checked October 13, 2026:
[Semantic Versioning](https://semver.org/) defines version increments and immutable
published versions. [Android app versioning](https://developer.android.com/studio/publish/versioning)
distinguishes user-visible versionName from the increasing numeric versionCode.
