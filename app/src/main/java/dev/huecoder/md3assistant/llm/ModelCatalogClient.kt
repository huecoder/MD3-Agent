package dev.huecoder.md3assistant.llm

import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.net.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

class ModelCatalogClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listModels(provider: ProviderProfile): List<String> = withContext(Dispatchers.IO) {
        val request = ProviderEndpoint(provider).authorizedRequest(MODELS_PATH).get().build()
        try {
            httpClients.current().newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw ProviderException.Http(response.code, "HTTP ${response.code}: ${body.take(ERROR_PREVIEW_CHARS)}")
                }
                json.decodeFromString(ModelList.serializer(), body).data.map { it.id }.sorted()
            }
        } catch (e: IOException) {
            throw ProviderException.Network(e)
        } catch (e: SerializationException) {
            throw ProviderException.MalformedResponse("Server returned the model list in an unknown format")
        }
    }

    @Serializable
    private data class ModelList(val data: List<ModelEntry>)

    @Serializable
    private data class ModelEntry(val id: String)

    private companion object {
        const val MODELS_PATH = "models"
        const val ERROR_PREVIEW_CHARS = 300
    }
}
