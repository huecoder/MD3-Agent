package dev.huecoder.md3assistant.llm

import dev.huecoder.md3assistant.data.settings.ProviderProfile
import dev.huecoder.md3assistant.llm.models.ModelInfo
import dev.huecoder.md3assistant.llm.models.ModelInfoParser
import dev.huecoder.md3assistant.net.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

class ModelCatalogClient(private val httpClients: HttpClientProvider) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listModels(provider: ProviderProfile): List<String> = listModelInfo(provider).map { it.id }

    suspend fun listModelInfo(provider: ProviderProfile): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = ProviderEndpoint(provider).authorizedRequest(MODELS_PATH).get().build()
        try {
            httpClients.current().newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw ProviderException.Http(response.code, "HTTP ${response.code}: ${body.take(ERROR_PREVIEW_CHARS)}")
                }
                ModelInfoParser.parseList(json.parseToJsonElement(body))
            }
        } catch (e: IOException) {
            throw ProviderException.Network(e)
        } catch (e: SerializationException) {
            throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        } catch (e: IllegalArgumentException) {
            throw ProviderException.MalformedResponse(UNKNOWN_FORMAT)
        }
    }

    private companion object {
        const val MODELS_PATH = "models"
        const val ERROR_PREVIEW_CHARS = 300
        const val UNKNOWN_FORMAT = "Server returned the model list in an unknown format"
    }
}
