package dev.huecoder.md3agent.conversation

interface ToolGate {
    fun privateContextAllowed(): Boolean

    suspend fun admit(toolName: String): Boolean

    data object Open : ToolGate {
        override fun privateContextAllowed(): Boolean = true

        override suspend fun admit(toolName: String): Boolean = true
    }
}
