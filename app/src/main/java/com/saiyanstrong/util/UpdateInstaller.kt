package com.saiyanstrong.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.saiyanstrong.domain.model.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dm: DownloadManager =
        context.getSystemService(DownloadManager::class.java)

    fun startDownload(update: AppUpdate): Long {
        val fileName = "SaiyanStrong-${update.tagName}.apk"
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl)).apply {
            setTitle("SaiyanStrong ${update.tagName}")
            setDescription("Downloading update…")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
        }
        return dm.enqueue(request)
    }

    fun queryStatus(downloadId: Long): Int {
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        return if (cursor.moveToFirst()) {
            val col = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            cursor.getInt(col).also { cursor.close() }
        } else {
            cursor.close()
            -1
        }
    }

    fun getDownloadedUri(downloadId: Long): Uri? =
        dm.getUriForDownloadedFile(downloadId)

    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()
}
