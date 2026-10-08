package dev.huecoder.md3agent.data.updates

import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.net.HttpStatusException
import dev.huecoder.md3agent.net.fetchText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.IOException

sealed interface UpdateStatus {
    data class UpToDate(val latest: String) : UpdateStatus
    data class Available(val version: String, val pageUrl: String, val apkUrl: String?) : UpdateStatus
    data object NotPublished : UpdateStatus
    data object Unreachable : UpdateStatus
}

class UpdateChecker(private val http: HttpClientProvider, installedVersion: String) {
    private val installed = AppVersion.parse(installedVersion)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateStatus {
        val release = try {
            val request = Request.Builder().url(LATEST_RELEASE_URL).header("Accept", GITHUB_JSON).build()
            json.decodeFromString(Release.serializer(), http.current().fetchText(request))
        } catch (e: HttpStatusException) {
            return if (e.code == HTTP_NOT_FOUND) UpdateStatus.NotPublished else UpdateStatus.Unreachable
        } catch (e: IOException) {
            return UpdateStatus.Unreachable
        } catch (e: SerializationException) {
            return UpdateStatus.Unreachable
        }
        val latest = AppVersion.parse(release.tag) ?: return UpdateStatus.Unreachable
        if (installed != null && latest <= installed) return UpdateStatus.UpToDate(latest.label)
        val apk = release.assets.firstOrNull { it.name.endsWith(APK_SUFFIX) }?.downloadUrl
        return UpdateStatus.Available(latest.label, release.pageUrl, apk)
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val pageUrl: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val downloadUrl: String)

    companion object {
        const val REPOSITORY_URL = "https://github.com/huecoder/md3-agent"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/huecoder/md3-agent/releases/latest"
        private const val GITHUB_JSON = "application/vnd.github+json"
        private const val HTTP_NOT_FOUND = 404
        private const val APK_SUFFIX = ".apk"
    }
}
