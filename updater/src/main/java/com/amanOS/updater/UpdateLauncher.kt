package com.amanOS.updater

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object UpdateLauncher {

    fun openDownloadPage(context: Context, downloadUrl: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

