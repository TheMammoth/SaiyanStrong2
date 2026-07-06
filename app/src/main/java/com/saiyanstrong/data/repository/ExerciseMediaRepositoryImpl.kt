package com.saiyanstrong.data.repository

import android.content.Context
import com.saiyanstrong.domain.model.ExerciseMedia
import com.saiyanstrong.domain.repository.ExerciseMediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val DATASET_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
private const val IMAGE_BASE_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
private const val CACHE_FILE_NAME = "free_exercise_db.json"
private const val MIN_MATCH_SCORE = 0.75

private data class MediaEntry(
    val tokens: Set<String>,
    val imageUrls: List<String>,
    val instructions: List<String>
)

@Singleton
class ExerciseMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ExerciseMediaRepository {

    private val mutex = Mutex()
    private var entries: List<MediaEntry>? = null

    override suspend fun getMediaFor(exerciseName: String): ExerciseMedia? =
        withContext(Dispatchers.IO) {
            val dataset = loadEntries() ?: return@withContext null
            val ourTokens = tokenize(exerciseName)
            if (ourTokens.isEmpty()) return@withContext null

            val best = dataset
                .map { entry -> entry to (ourTokens.intersect(entry.tokens).size.toDouble() / ourTokens.size) }
                .filter { (_, score) -> score >= MIN_MATCH_SCORE }
                // Prefer the highest coverage of our name, then the least noisy entry name
                .maxWithOrNull(compareBy({ it.second }, { -it.first.tokens.size }))
                ?.first ?: return@withContext null

            ExerciseMedia(imageUrls = best.imageUrls, instructions = best.instructions)
        }

    private suspend fun loadEntries(): List<MediaEntry>? = mutex.withLock {
        entries?.let { return it }
        val json = readCache() ?: downloadDataset()?.also(::writeCache) ?: return null
        val parsed = runCatching { parse(json) }.getOrNull() ?: return null
        entries = parsed
        parsed
    }

    private fun readCache(): String? {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    private fun writeCache(json: String) {
        runCatching { File(context.filesDir, CACHE_FILE_NAME).writeText(json) }
    }

    private fun downloadDataset(): String? = runCatching {
        val connection = URL(DATASET_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "SaiyanStrong-Android")
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun parse(json: String): List<MediaEntry> {
        val array = JSONArray(json)
        val result = ArrayList<MediaEntry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name")
            if (name.isBlank()) continue
            val images = obj.optJSONArray("images") ?: continue
            val instructions = obj.optJSONArray("instructions")
            result += MediaEntry(
                tokens = tokenize(name),
                imageUrls = (0 until images.length()).map { IMAGE_BASE_URL + images.getString(it) },
                instructions = instructions?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
                }.orEmpty()
            )
        }
        return result
    }

    private fun tokenize(name: String): Set<String> =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(" ")
            .filter { it.length > 1 }
            .toSet()
}
