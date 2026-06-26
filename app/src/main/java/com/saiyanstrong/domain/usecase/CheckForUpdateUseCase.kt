package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckForUpdateUseCase @Inject constructor() {

    // Returns null = genuinely up to date. Throws on network/API errors so caller can show an error state.
    suspend fun execute(currentVersionName: String): AppUpdate? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/TheMammoth/SaiyanStrong2/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SaiyanStrong-Android")
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw Exception("GitHub API returned HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        val tagName = json.getString("tag_name")
        if (tagName.removePrefix("v") == currentVersionName) return@withContext null

        val assets = json.getJSONArray("assets")
        val downloadUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
            ?: throw Exception("No APK found in release $tagName — check GitHub Assets")

        AppUpdate(tagName = tagName, downloadUrl = downloadUrl)
    }
}
