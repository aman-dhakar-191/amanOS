package com.amanOS.updater

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String
)

