package com.saiyanstrong.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.saiyanstrong.domain.model.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_REDIRECTS = 5

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Downloads the APK straight into the app cache (following GitHub's CDN
     * redirects manually — DownloadManager chokes on them) and returns a
     * FileProvider uri ready for ACTION_VIEW install. Null on failure.
     */
    suspend fun downloadToCache(
        update: AppUpdate,
        onProgress: (Int) -> Unit = {}
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            var url = URL(update.downloadUrl)
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "SaiyanStrong-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = connection.responseCode
                if (code in 301..308) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect without Location header")
                    connection.disconnect()
                    if (++redirects > MAX_REDIRECTS) error("Too many redirects")
                    url = URL(location)
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) error("HTTP $code")
                break
            }

            val totalBytes = connection.contentLengthLong
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "SaiyanStrong-${update.tagName}.apk")
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var bytesDone = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDone += bytesRead
                        if (totalBytes > 0) onProgress(((bytesDone * 100) / totalBytes).toInt())
                    }
                }
            }
            connection.disconnect()

            FileProvider.getUriForFile(context, "com.saiyanstrong.fileprovider", apkFile)
        }.getOrNull()
    }

    fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()
}
