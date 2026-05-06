package com.amanOS.updater

interface UpdateSource {
    suspend fun fetchLatest(): UpdateInfo?
}

