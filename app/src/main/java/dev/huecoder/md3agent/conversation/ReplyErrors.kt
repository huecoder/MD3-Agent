package dev.huecoder.md3agent.conversation

import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.net.NetworkFailureKind

data class StoredNetworkError(val kind: NetworkFailureKind, val detail: String)

object ReplyErrors {
    private const val NETWORK_PREFIX = "network."
    private const val SEPARATOR = ": "
    private const val DETAIL_MAX_CHARS = 200

    fun encode(failure: Exception): String = when (failure) {
        is ProviderException.Network -> "$NETWORK_PREFIX${failure.kind.name.lowercase()}$SEPARATOR${detailOf(failure)}"
        else -> failure.message ?: failure.javaClass.simpleName
    }

    fun decodeNetwork(stored: String): StoredNetworkError? {
        if (!stored.startsWith(NETWORK_PREFIX) || SEPARATOR !in stored) return null
        val kindName = stored.removePrefix(NETWORK_PREFIX).substringBefore(SEPARATOR)
        val kind = NetworkFailureKind.entries.firstOrNull { it.name.lowercase() == kindName } ?: return null
        return StoredNetworkError(kind, stored.substringAfter(SEPARATOR))
    }

    private fun detailOf(failure: ProviderException.Network): String {
        val cause = failure.cause ?: failure
        return listOfNotNull(cause.javaClass.simpleName, cause.message?.takeIf { it.isNotBlank() })
            .joinToString(SEPARATOR)
            .take(DETAIL_MAX_CHARS)
    }
}
