# Updater Framework

`updater` is a reusable library for checking app updates from GitHub Releases.

## What it provides
- `GitHubReleaseUpdateSource`: pulls latest release metadata
- `UpdateChecker`: compares remote version with local version code
- `UpdateLauncher`: opens the update download URL

## Example

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
    UpdateCheckResult.UpToDate -> {
        // no-op
    }
    is UpdateCheckResult.Failed -> {
        // show result.message
    }
}
```

## Notes
- Version comparison uses digits extracted from GitHub `tag_name`.
- Publish tags like `v1002` for reliable ordering.

