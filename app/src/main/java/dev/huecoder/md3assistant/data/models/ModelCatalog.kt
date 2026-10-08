package dev.huecoder.md3assistant.data.models

import android.util.Log
import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.llm.ModelCatalogClient
import dev.huecoder.md3assistant.llm.ProviderException
import dev.huecoder.md3assistant.llm.models.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class ProviderCatalog(val models: List<ModelInfo>, val fetchedAt: Long) {
    fun find(modelId: String): ModelInfo? = models.firstOrNull { it.id == modelId }
}

class ModelCatalog(
    private val client: ModelCatalogClient,
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val catalogs = MutableStateFlow<Map<String, ProviderCatalog>>(emptyMap())

    fun observe(providerId: String): Flow<ProviderCatalog?> =
        catalogs.map { it[providerId] }.onStart { loadIfMissing(providerId) }

    fun info(providerId: String?, modelId: String): ModelInfo =
        providerId?.let { catalogs.value[it]?.find(modelId) } ?: ModelInfo(modelId)

    suspend fun infoLoaded(providerId: String?, modelId: String): ModelInfo {
        providerId?.let { loadIfMissing(it) }
        return info(providerId, modelId)
    }

    suspend fun refresh(provider: ProviderProfile): List<ModelInfo> {
        val models = client.listModelInfo(provider)
        val catalog = ProviderCatalog(models, clock())
        catalogs.update { it + (provider.id to catalog) }
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            fileFor(provider.id).writeText(json.encodeToString(ProviderCatalog.serializer(), catalog))
        }
        return models
    }

    suspend fun refreshIfStale(provider: ProviderProfile) {
        loadIfMissing(provider.id)
        val fetchedAt = catalogs.value[provider.id]?.fetchedAt ?: 0L
        if (clock() - fetchedAt < STALE_AFTER_MS) return
        try {
            refresh(provider)
        } catch (e: ProviderException) {
            Log.w(TAG, "Model list for ${provider.name} could not be refreshed", e)
        }
    }

    suspend fun forget(providerId: String) {
        catalogs.update { it - providerId }
        withContext(Dispatchers.IO) { fileFor(providerId).delete() }
    }

    private suspend fun loadIfMissing(providerId: String) {
        if (providerId in catalogs.value) return
        val cached = withContext(Dispatchers.IO) {
            val file = fileFor(providerId)
            if (!file.exists()) return@withContext null
            try {
                json.decodeFromString(ProviderCatalog.serializer(), file.readText())
            } catch (e: SerializationException) {
                Log.w(TAG, "Cached model list is corrupted, dropping it", e)
                file.delete()
                null
            } catch (e: IOException) {
                Log.w(TAG, "Cached model list could not be read", e)
                null
            }
        } ?: return
        catalogs.update { if (providerId in it) it else it + (providerId to cached) }
    }

    private fun fileFor(providerId: String) = File(directory, "$providerId.json")

    private companion object {
        const val TAG = "ModelCatalog"
        const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
