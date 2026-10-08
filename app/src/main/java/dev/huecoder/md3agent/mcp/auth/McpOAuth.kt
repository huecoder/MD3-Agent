package dev.huecoder.md3agent.mcp.auth

import android.util.Base64
import dev.huecoder.md3agent.data.settings.McpOAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

class McpOAuthException(message: String) : IOException(message)

data class AuthServer(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String?,
    val scopes: List<String>,
    val resource: String,
)

data class OAuthClient(val clientId: String, val clientSecret: String?)

class Pkce private constructor(val verifier: String, val challenge: String) {
    companion object {
        private const val VERIFIER_BYTES = 48

        fun create(random: SecureRandom = SecureRandom()): Pkce {
            val verifier = base64Url(ByteArray(VERIFIER_BYTES).also(random::nextBytes))
            val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
            return Pkce(verifier, challenge)
        }

        fun base64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

class McpOAuth(private val http: () -> OkHttpClient, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(serverUrl: String, resourceMetadataUrl: String?): AuthServer {
        val server = serverUrl.trim().toHttpUrlOrNull() ?: throw McpOAuthException("The server address is not valid")
        val resourceMetadata = (listOfNotNull(resourceMetadataUrl) + wellKnown(server, PROTECTED_RESOURCE))
            .firstNotNullOfOrNull { fetchJson(it) }
        val issuer = (resourceMetadata?.get("authorization_servers") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?.toHttpUrlOrNull() ?: server.newBuilder().encodedPath("/").build()
        val metadata = (wellKnown(issuer, AUTHORIZATION_SERVER) + wellKnown(issuer, OPENID_CONFIGURATION)).firstNotNullOfOrNull { fetchJson(it) }
            ?: throw McpOAuthException("The server does not publish how to sign in")
        return AuthServer(
            authorizationEndpoint = metadata.string("authorization_endpoint") ?: throw McpOAuthException("No authorization endpoint"),
            tokenEndpoint = metadata.string("token_endpoint") ?: throw McpOAuthException("No token endpoint"),
            registrationEndpoint = metadata.string("registration_endpoint"),
            scopes = (resourceMetadata?.get("scopes_supported") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            resource = resourceMetadata?.string("resource") ?: serverUrl.trim(),
        )
    }

    suspend fun register(server: AuthServer, redirectUri: String, clientName: String): OAuthClient {
        val endpoint = server.registrationEndpoint ?: throw McpOAuthException("The server does not support automatic app registration")
        val body = buildJsonObject {
            put("client_name", clientName)
            putJsonArray("redirect_uris") { add(JsonPrimitive(redirectUri)) }
            putJsonArray("grant_types") { add(JsonPrimitive("authorization_code")); add(JsonPrimitive("refresh_token")) }
            putJsonArray("response_types") { add(JsonPrimitive("code")) }
            put("token_endpoint_auth_method", "none")
        }
        val reply = post(Request.Builder().url(endpoint).post(body.toString().toRequestBody(JSON_MEDIA)).build())
        return OAuthClient(reply.string("client_id") ?: throw McpOAuthException("Registration returned no client id"), reply.string("client_secret"))
    }

    fun authorizationUrl(server: AuthServer, client: OAuthClient, redirectUri: String, pkce: Pkce, state: String): String =
        server.authorizationEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", client.clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .addQueryParameter("resource", server.resource)
            .apply { if (server.scopes.isNotEmpty()) addQueryParameter("scope", server.scopes.joinToString(" ")) }
            .build()
            .toString()

    suspend fun exchange(server: AuthServer, client: OAuthClient, code: String, redirectUri: String, pkce: Pkce): McpOAuthSession {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", client.clientId)
            .add("code_verifier", pkce.verifier)
            .add("resource", server.resource)
            .apply { client.clientSecret?.let { add("client_secret", it) } }
            .build()
        val reply = post(Request.Builder().url(server.tokenEndpoint).post(form).build())
        return session(reply, client, server.tokenEndpoint, previousRefresh = null)
    }

    suspend fun refresh(session: McpOAuthSession, resource: String): McpOAuthSession {
        val refreshToken = session.refreshToken ?: throw McpOAuthException("Sign in again, there is no refresh token")
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", session.clientId)
            .add("resource", resource)
            .apply { session.clientSecret?.let { add("client_secret", it) } }
            .build()
        val reply = post(Request.Builder().url(session.tokenEndpoint).post(form).build())
        return session(reply, OAuthClient(session.clientId, session.clientSecret), session.tokenEndpoint, previousRefresh = refreshToken)
    }

    private fun session(reply: JsonObject, client: OAuthClient, tokenEndpoint: String, previousRefresh: String?) = McpOAuthSession(
        clientId = client.clientId,
        clientSecret = client.clientSecret,
        tokenEndpoint = tokenEndpoint,
        accessToken = reply.string("access_token") ?: throw McpOAuthException(reply.string("error_description") ?: "No access token in the reply"),
        refreshToken = reply.string("refresh_token") ?: previousRefresh,
        expiresAt = (reply["expires_in"] as? JsonPrimitive)?.longOrNull?.let { clock() + it * MS_PER_SECOND },
    )

    private fun wellKnown(base: HttpUrl, suffix: String): List<String> {
        val path = base.encodedPath.trimEnd('/')
        val withPath = base.newBuilder().encodedPath("/.well-known/$suffix$path").query(null).build()
        val atRoot = base.newBuilder().encodedPath("/.well-known/$suffix").query(null).build()
        return listOf(withPath, atRoot).map { it.toString() }.distinct()
    }

    private suspend fun fetchJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        runCatching {
            http().newCall(request).execute().use { response ->
                if (response.isSuccessful) json.parseToJsonElement(response.body.string()) as? JsonObject else null
            }
        }.getOrNull()
    }

    private suspend fun post(request: Request): JsonObject = withContext(Dispatchers.IO) {
        http().newCall(request.newBuilder().header("Accept", "application/json").build()).execute().use { response ->
            val text = response.body.string()
            val parsed = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (!response.isSuccessful) {
                throw McpOAuthException(parsed?.string("error_description") ?: parsed?.string("error") ?: "HTTP ${response.code}")
            }
            parsed ?: throw McpOAuthException("The server answered with something that is not JSON")
        }
    }

    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val PROTECTED_RESOURCE = "oauth-protected-resource"
        const val AUTHORIZATION_SERVER = "oauth-authorization-server"
        const val OPENID_CONFIGURATION = "openid-configuration"
        const val MS_PER_SECOND = 1_000L
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
