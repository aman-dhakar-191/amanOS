package com.amanOS.updater

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String, val cause: Throwable? = null) : UpdateCheckResult
}

