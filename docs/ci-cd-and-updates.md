# CI/CD and Update Framework

This repository now includes:

- CI build pipeline: `.github/workflows/android-ci.yml`
- Release pipeline: `.github/workflows/android-release.yml`
- Reusable updater library module: `:updater`

## CI pipeline

Triggers on pushes to `master`/`develop` and pull requests.

Builds:
- `:core`
- `:updater`
- `:contacts`
- `:messaging`
- `:agenttest`

Also runs unit tests and uploads debug APK artifacts.

## Release pipeline

Triggers on every push to `master` and also supports manual `workflow_dispatch`.
On each run, the workflow:
- checks whether the commit already has a `v*` tag
- if not tagged, creates the next numeric tag (`v1001`, `v1002`, ...)
- publishes a GitHub Release using that tag

Publishes release assets to GitHub Releases:
- `contacts-release.apk`
- `messaging-release.apk`
- `agenttest-release.apk`
- `core-release.aar`
- `updater-release.aar`

Required repo secrets for release signing:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Versioning for updates

The updater compares versions using digits extracted from Git tags.

Recommended tag format:
- `v1001`
- `v1002`

This keeps ordering deterministic (`1002 > 1001`).

## Updater module quick usage

```kotlin
val source = GitHubReleaseUpdateSource(
    owner = "your-org",
    repo = "your-repo",
    assetNameContains = "agenttest"
)

val checker = UpdateChecker(
    updateSource = source,
    currentVersionCode = BuildConfig.VERSION_CODE.toLong()
)

when (val result = checker.check()) {
    is UpdateCheckResult.UpdateAvailable -> {
        UpdateLauncher.openDownloadPage(context, result.update.downloadUrl)
    }
    UpdateCheckResult.UpToDate -> Unit
    is UpdateCheckResult.Failed -> Unit
}
```

## Notes

- Release workflow publishes signed release APKs to GitHub Releases.
- For long-term operation, follow `docs/signing-and-distribution-guide.md`.

