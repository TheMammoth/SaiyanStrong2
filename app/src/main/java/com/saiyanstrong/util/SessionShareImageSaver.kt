package com.saiyanstrong.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionShareImageSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Saves the card to cache/shares/, wraps it as a FileProvider uri, and launches the share sheet. */
    suspend fun share(bitmap: Bitmap, fileName: String = "saiyanstrong-session.png") {
        val uri = withContext(Dispatchers.IO) {
            val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
            val file = File(sharesDir, fileName)
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, "com.saiyanstrong.fileprovider", file)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, "Share your session").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
