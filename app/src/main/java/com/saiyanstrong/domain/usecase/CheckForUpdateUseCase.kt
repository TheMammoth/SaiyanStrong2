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

    suspend fun execute(currentVersionName: String): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/TheMammoth/SaiyanStrong2/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            if (tagName.removePrefix("v") == currentVersionName) return@withContext null

            val assets = json.getJSONArray("assets")
            val downloadUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return@withContext null

            AppUpdate(tagName = tagName, downloadUrl = downloadUrl)
        } catch (_: Exception) {
            null
        }
    }
}
