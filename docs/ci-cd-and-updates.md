# CI/CD and Update Framework

This repository now includes:

- CI build pipeline: `.github/workflows/android-ci.yml`
- Release pipeline: `.github/workflows/android-release.yml`
- Reusable updater library module: `:updater`

## CI pipeline

Triggers on pushes to `main`/`develop` and pull requests.

Builds:
- `:core`
- `:updater`
- `:contacts`
- `:messaging`
- `:agenttest`

Also runs unit tests and uploads debug APK artifacts.

## Release pipeline

Triggers on tags like `v1002` and also supports manual `workflow_dispatch`.

Publishes release assets to GitHub Releases:
- `contacts-debug.apk`
- `messaging-debug.apk`
- `agenttest-debug.apk`
- `core-release.aar`
- `updater-release.aar`

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

- Current release workflow publishes debug APKs to GitHub Releases.
- For production rollout, add signed release APK/AAB generation and store deployment.

