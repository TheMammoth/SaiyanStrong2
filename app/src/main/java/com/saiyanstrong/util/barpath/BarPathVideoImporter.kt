package com.saiyanstrong.util.barpath

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaMetadataRetriever/BarPathFrameTracker both need a real file path, not a content://
 * Uri, so a gallery-picked video is copied into cache first — mirrors the cache-copy
 * pattern SessionShareImageSaver already uses for the opposite direction (file -> share).
 */
@Singleton
class BarPathVideoImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun importFromGallery(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val importsDir = File(context.cacheDir, "bar_path").apply { mkdirs() }
            val file = File(importsDir, "imported_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            file.absolutePath
        }.getOrNull()
    }
}
