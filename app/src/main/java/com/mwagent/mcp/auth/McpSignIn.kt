package com.mwagent.mcp.auth

import android.net.Uri
import com.mwagent.data.settings.McpServer
import com.mwagent.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

sealed interface SignInStatus {
    data object Idle : SignInStatus
    data class InProgress(val serverId: String) : SignInStatus
    data class Failed(val serverId: String, val message: String) : SignInStatus
}

class McpSignIn(private val oauth: McpOAuth, private val settings: SettingsRepository) {
    private val pending = AtomicReference<PendingSignIn?>(null)
    private val _status = MutableStateFlow<SignInStatus>(SignInStatus.Idle)

    val status: StateFlow<SignInStatus> = _status.asStateFlow()

    suspend fun begin(server: McpServer, resourceMetadataUrl: String?): String {
        _status.value = SignInStatus.InProgress(server.id)
        return try {
            val authServer = oauth.discover(server.url, resourceMetadataUrl)
            val client = oauth.register(authServer, REDIRECT_URI, CLIENT_NAME)
            val pkce = Pkce.create()
            val state = UUID.randomUUID().toString()
            pending.set(PendingSignIn(server.id, authServer, client, pkce, state))
            oauth.authorizationUrl(authServer, client, REDIRECT_URI, pkce, state)
        } catch (e: IOException) {
            _status.value = SignInStatus.Failed(server.id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun isCallback(uri: Uri?): Boolean = uri?.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST

    suspend fun complete(uri: Uri) {
        val signIn = pending.getAndSet(null) ?: return
        val failure = when {
            uri.getQueryParameter("state") != signIn.state -> "The sign-in reply does not match the request, try again"
            uri.getQueryParameter("error") != null -> uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            uri.getQueryParameter("code") == null -> "The sign-in reply has no code"
            else -> null
        }
        if (failure != null) {
            _status.value = SignInStatus.Failed(signIn.serverId, failure)
            return
        }
        try {
            val session = oauth.exchange(signIn.server, signIn.client, uri.getQueryParameter("code").orEmpty(), REDIRECT_URI, signIn.pkce)
            settings.updateTools { tools ->
                tools.copy(mcpServers = tools.mcpServers.map { if (it.id == signIn.serverId) it.copy(oauth = session) else it })
            }
            _status.value = SignInStatus.Idle
        } catch (e: IOException) {
            _status.value = SignInStatus.Failed(signIn.serverId, e.message ?: e.javaClass.simpleName)
        }
    }

    fun dismissFailure() {
        if (_status.value is SignInStatus.Failed) _status.value = SignInStatus.Idle
    }

    private class PendingSignIn(val serverId: String, val server: AuthServer, val client: OAuthClient, val pkce: Pkce, val state: String)

    companion object {
        private const val REDIRECT_SCHEME = "mwagent"
        private const val REDIRECT_HOST = "oauth"
        private const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST/callback"
        private const val CLIENT_NAME = "MW AGENT"
    }
}
