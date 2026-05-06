package com.amanOS.updater

class UpdateChecker(
    private val updateSource: UpdateSource,
    private val currentVersionCode: Long
) {

    suspend fun check(): UpdateCheckResult {
        return runCatching {
            val latest = updateSource.fetchLatest()
            if (latest == null) {
                UpdateCheckResult.Failed("No update metadata available")
            } else if (latest.versionCode > currentVersionCode) {
                UpdateCheckResult.UpdateAvailable(latest)
            } else {
                UpdateCheckResult.UpToDate
            }
        }.getOrElse {
            UpdateCheckResult.Failed(message = it.message ?: "Update check failed", cause = it)
        }
    }
}

