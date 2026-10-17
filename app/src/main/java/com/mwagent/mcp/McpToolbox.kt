package com.mwagent.mcp

import android.util.Log
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.conversation.UserPrompts
import com.mwagent.data.settings.McpOAuthSession
import com.mwagent.data.settings.McpServer
import com.mwagent.mcp.auth.McpOAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import com.mwagent.net.HttpClientProvider
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface McpServerState {
    data object Loading : McpServerState

    data class Ready(val tools: List<McpTool>) : McpServerState

    data class Failed(val message: String) : McpServerState

    data class NeedsSignIn(val resourceMetadataUrl: String?) : McpServerState
}

class McpToolbox(
    private val httpClients: HttpClientProvider,
    private val attachments: AttachmentStore,
    private val prompts: UserPrompts,
    private val oauth: McpOAuth,
    private val saveSession: suspend (serverId: String, session: McpOAuthSession) -> Unit,
    private val scope: CoroutineScope,
) : ToolSource {
    private val sessions = ConcurrentHashMap<String, McpOAuthSession>()
    private val _states = MutableStateFlow<Map<String, McpServerState>>(emptyMap())
    private val loaded = mutableMapOf<String, McpServer>()
    private val jobs = mutableMapOf<String, Job>()

    val states: StateFlow<Map<String, McpServerState>> = _states.asStateFlow()

    override fun tools(): List<Tool> {
        val seen = mutableSetOf<String>()
        return _states.value.values.filterIsInstance<McpServerState.Ready>().flatMap { it.tools }.filter { seen.add(it.spec.name) }
    }

    @Synchronized
    fun sync(servers: List<McpServer>) {
        val wanted = servers.filter { it.enabled && it.url.isNotBlank() }.associateBy { it.id }
        wanted.values.forEach { server -> server.oauth?.let { sessions[server.id] = it } ?: sessions.remove(server.id) }
        (loaded.keys - wanted.keys).forEach(::forget)
        wanted.values.filter { loaded[it.id]?.withoutTokenValues() != it.withoutTokenValues() }.forEach(::load)
    }

    @Synchronized
    fun reload(serverId: String) {
        loaded[serverId]?.let(::load)
    }

    override suspend fun awaitReady() {
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { _states.first { states -> states.values.none { it is McpServerState.Loading } } }
    }

    suspend fun probe(server: McpServer): List<McpRemoteTool> = McpClient(server, authorizerFor(server), httpClients::current).listTools()

    private fun authorizerFor(server: McpServer): McpAuthorizer = SessionAuthorizer(server)

    private fun load(server: McpServer) {
        jobs.remove(server.id)?.cancel()
        loaded[server.id] = server
        _states.update { it + (server.id to McpServerState.Loading) }
        jobs[server.id] = scope.launch {
            val state = try {
                val client = McpClient(server, authorizerFor(server), httpClients::current)
                McpServerState.Ready(client.listTools().map { McpTool(server.name, it, client, attachments, prompts) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: McpUnauthorizedException) {
                McpServerState.NeedsSignIn(e.resourceMetadataUrl)
            } catch (e: Exception) {
                Log.w(TAG, "MCP server ${server.name} failed to load", e)
                McpServerState.Failed(e.message ?: e.javaClass.simpleName)
            }
            _states.update { if (it.containsKey(server.id)) it + (server.id to state) else it }
        }
    }

    private inner class SessionAuthorizer(private val server: McpServer) : McpAuthorizer {
        private val refreshLock = Mutex()

        override suspend fun authorization(): String? {
            val session = sessions[server.id] ?: server.oauth ?: return null
            val expiresAt = session.expiresAt
            if (expiresAt != null && System.currentTimeMillis() > expiresAt - EXPIRY_MARGIN_MS) refresh()
            return (sessions[server.id] ?: session).accessToken.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
        }

        override suspend fun refresh(): Boolean = refreshLock.withLock {
            val session = sessions[server.id] ?: server.oauth ?: return@withLock false
            if (session.refreshToken == null) return@withLock false
            try {
                val renewed = oauth.refresh(session, server.url.trim())
                sessions[server.id] = renewed
                saveSession(server.id, renewed)
                true
            } catch (e: IOException) {
                Log.w(TAG, "Token refresh for ${server.name} failed", e)
                false
            }
        }
    }

    private fun forget(serverId: String) {
        jobs.remove(serverId)?.cancel()
        loaded.remove(serverId)
        _states.update { it - serverId }
    }

    private companion object {
        const val TAG = "McpToolbox"
        const val SETTLE_TIMEOUT_MS = 15_000L
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
