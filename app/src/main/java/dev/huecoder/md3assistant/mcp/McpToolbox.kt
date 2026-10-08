package dev.huecoder.md3assistant.mcp

import android.util.Log
import dev.huecoder.md3assistant.data.attachments.AttachmentStore
import dev.huecoder.md3assistant.data.settings.McpServer
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.tools.Tool
import dev.huecoder.md3assistant.tools.ToolSource
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
}

class McpToolbox(
    private val httpClients: HttpClientProvider,
    private val attachments: AttachmentStore,
    private val scope: CoroutineScope,
) : ToolSource {
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
        (loaded.keys - wanted.keys).forEach(::forget)
        wanted.values.filter { loaded[it.id] != it }.forEach(::load)
    }

    @Synchronized
    fun reload(serverId: String) {
        loaded[serverId]?.let(::load)
    }

    override suspend fun awaitReady() {
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { _states.first { states -> states.values.none { it is McpServerState.Loading } } }
    }

    suspend fun probe(server: McpServer): List<McpRemoteTool> = McpClient(server, httpClients::current).listTools()

    private fun load(server: McpServer) {
        jobs.remove(server.id)?.cancel()
        loaded[server.id] = server
        _states.update { it + (server.id to McpServerState.Loading) }
        jobs[server.id] = scope.launch {
            val state = try {
                val client = McpClient(server, httpClients::current)
                McpServerState.Ready(client.listTools().map { McpTool(server.name, it, client, attachments) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "MCP server ${server.name} failed to load", e)
                McpServerState.Failed(e.message ?: e.javaClass.simpleName)
            }
            _states.update { if (it.containsKey(server.id)) it + (server.id to state) else it }
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
    }
}
