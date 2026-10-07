package dev.huecoder.md3assistant

import android.content.Context
import dev.huecoder.md3assistant.assistant.ScreenContextStore
import dev.huecoder.md3assistant.conversation.ConversationRunner
import dev.huecoder.md3assistant.data.attachments.AttachmentStore
import dev.huecoder.md3assistant.data.chat.ChatRepository
import dev.huecoder.md3assistant.data.db.AppDatabase
import dev.huecoder.md3assistant.data.settings.SecretCipher
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.llm.ChatCompletionClient
import dev.huecoder.md3assistant.llm.ModelCatalogClient
import dev.huecoder.md3assistant.llm.TranscriptionClient
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.prompt.DeviceInfoProvider
import dev.huecoder.md3assistant.prompt.SystemPromptRenderer
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.ShowUiTool
import dev.huecoder.md3assistant.tools.ToolRegistry
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import dev.huecoder.md3assistant.tools.web.WebSearcher
import dev.huecoder.md3assistant.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, SecretCipher())
    val httpClients = HttpClientProvider(settings.networkSettings, applicationScope)

    private val database = AppDatabase.build(context)
    val attachments = AttachmentStore(context)
    val chats = ChatRepository(database.chatDao(), database.messageDao(), attachments)

    val completions = ChatCompletionClient(httpClients)
    val modelCatalog = ModelCatalogClient(httpClients)
    val transcriptions = TranscriptionClient(httpClients)
    val systemPrompt = SystemPromptRenderer(DeviceInfoProvider(context))

    val screenContext = ScreenContextStore()

    val tools = ToolRegistry(
        listOf(
            WebSearchTool(WebSearcher(httpClients)),
            ReadWebpageTool(httpClients),
            WeatherTool(httpClients),
            ReadScreenTool(screenContext),
            ShowUiTool(),
        ),
    )

    fun newVoiceRecorder() = VoiceRecorder(appContext)

    val conversations = ConversationRunner(chats, settings, completions, systemPrompt, tools, attachments, applicationScope)
}
