package com.saiyanstrong.data.repository

import android.content.Context
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.ArchetypeInfo
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.repository.BiomechanicsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val ARCHETYPES_ASSET = "biomechanics/archetypes.json"
private const val KEYFRAMES_SQUAT_ASSET = "biomechanics/keyframes_squat.json"
private const val KEYFRAMES_DEADLIFT_ASSET = "biomechanics/keyframes_deadlift.json"
private const val KEYFRAMES_OHP_ASSET = "biomechanics/keyframes_ohp.json"

/**
 * Loads and parses the bundled JSON assets once, caches in memory — same
 * load-once-cache-forever shape as [ExerciseMediaRepositoryImpl], but from a local asset
 * (always present, bundled with the app) instead of a downloaded dataset, so there is no
 * network/cache-file fallback chain here.
 */
@Singleton
class BiomechanicsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BiomechanicsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private var archetypeInfoList: List<ArchetypeInfo>? = null
    private var animations: List<ArchetypeAnimation>? = null

    override suspend fun getAnimation(archetype: Archetype, lift: LiftType): ArchetypeAnimation =
        withContext(Dispatchers.IO) {
            loadAnimations().firstOrNull { it.archetype == archetype && it.lift == lift }
                ?: error("No biomechanics keyframe content for $archetype / $lift")
        }

    override suspend fun getArchetypeInfoList(): List<ArchetypeInfo> = withContext(Dispatchers.IO) {
        loadArchetypeInfo()
    }

    override fun getSupportedArchetypes(): List<Archetype> = Archetype.entries

    private suspend fun loadArchetypeInfo(): List<ArchetypeInfo> = mutex.withLock {
        archetypeInfoList?.let { return it }
        val text = context.assets.open(ARCHETYPES_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString<List<ArchetypeInfo>>(text).also { archetypeInfoList = it }
    }

    private suspend fun loadAnimations(): List<ArchetypeAnimation> = mutex.withLock {
        animations?.let { return it }
        val squat = readAnimationsAsset(KEYFRAMES_SQUAT_ASSET)
        // Each lift's asset is read defensively — a not-yet-authored lift (a missing file) just
        // means "no content for that lift yet," not an error.
        val deadlift = runCatching { readAnimationsAsset(KEYFRAMES_DEADLIFT_ASSET) }.getOrDefault(emptyList())
        val overheadPress = runCatching { readAnimationsAsset(KEYFRAMES_OHP_ASSET) }.getOrDefault(emptyList())
        (squat + deadlift + overheadPress).also { animations = it }
    }

    private fun readAnimationsAsset(assetPath: String): List<ArchetypeAnimation> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
