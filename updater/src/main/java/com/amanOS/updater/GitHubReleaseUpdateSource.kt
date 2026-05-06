package com.amanOS.updater

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GitHubReleaseUpdateSource(
    private val owner: String,
    private val repo: String,
    private val assetNameContains: String? = null
) : UpdateSource {

    override suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val assets = json.optJSONArray("assets")

                if (assets == null) {
                    null
                } else {
                    val matched = (0 until assets.length())
                        .asSequence()
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { asset ->
                            val name = asset.optString("name")
                            name.endsWith(".apk") && (assetNameContains == null || name.contains(assetNameContains, ignoreCase = true))
                        }

                    if (matched == null) {
                        null
                    } else {
                        val tagName = json.optString("tag_name")
                        val versionName = json.optString("name").ifBlank { tagName }
                        UpdateInfo(
                            versionName = versionName,
                            versionCode = inferVersionCode(tagName),
                            releaseNotes = json.optString("body"),
                            downloadUrl = matched.optString("browser_download_url"),
                            publishedAt = json.optString("published_at")
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun inferVersionCode(tagName: String): Long {
        val digits = tagName.filter { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }
}

