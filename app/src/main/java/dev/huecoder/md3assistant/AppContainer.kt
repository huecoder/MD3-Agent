package dev.huecoder.md3assistant

import android.content.Context
import dev.huecoder.md3assistant.assistant.ScreenContextStore
import dev.huecoder.md3assistant.conversation.ConversationRunner
import dev.huecoder.md3assistant.conversation.ToolApprovals
import dev.huecoder.md3assistant.data.attachments.AttachmentStore
import dev.huecoder.md3assistant.data.chat.ChatRepository
import dev.huecoder.md3assistant.data.db.AppDatabase
import dev.huecoder.md3assistant.data.settings.SecretCipher
import dev.huecoder.md3assistant.data.settings.SettingsRepository
import dev.huecoder.md3assistant.llm.ChatCompletionClient
import dev.huecoder.md3assistant.mcp.McpToolbox
import dev.huecoder.md3assistant.llm.ModelCatalogClient
import dev.huecoder.md3assistant.llm.TranscriptionClient
import dev.huecoder.md3assistant.net.HttpClientProvider
import dev.huecoder.md3assistant.prompt.DeviceInfoProvider
import dev.huecoder.md3assistant.prompt.SystemPromptRenderer
import dev.huecoder.md3assistant.tools.ReadScreenTool
import dev.huecoder.md3assistant.tools.ShowUiTool
import dev.huecoder.md3assistant.tools.system.RunCommandTool
import dev.huecoder.md3assistant.tools.system.RunPythonTool
import dev.huecoder.md3assistant.system.shell.ShellAccess
import dev.huecoder.md3assistant.tools.ToolRegistry
import dev.huecoder.md3assistant.system.files.DeviceFiles
import dev.huecoder.md3assistant.system.python.PythonRuntime
import dev.huecoder.md3assistant.tools.files.ListFilesTool
import dev.huecoder.md3assistant.tools.files.ReadFileTool
import dev.huecoder.md3assistant.tools.files.SendFilesTool
import dev.huecoder.md3assistant.tools.files.WriteFileTool
import dev.huecoder.md3assistant.tools.weather.WeatherTool
import dev.huecoder.md3assistant.tools.web.PageFetcher
import dev.huecoder.md3assistant.tools.web.ReadWebpageTool
import dev.huecoder.md3assistant.tools.web.WebSearchTool
import dev.huecoder.md3assistant.tools.web.WebSearcher
import dev.huecoder.md3assistant.data.settings.SpeechOutput
import dev.huecoder.md3assistant.data.settings.VoiceSettings
import dev.huecoder.md3assistant.voice.ProviderSpeechSynthesizer
import dev.huecoder.md3assistant.voice.SpeechSynthesizer
import dev.huecoder.md3assistant.voice.SystemSpeechSynthesizer
import dev.huecoder.md3assistant.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
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
    val shellAccess = ShellAccess(appContext)
    val deviceFiles = DeviceFiles(shellAccess.workspace, attachments.directory, File(appContext.cacheDir, FILE_STAGING_DIR))

    val python = PythonRuntime(appContext)

    val mcp = McpToolbox(httpClients, attachments, applicationScope)

    val tools = ToolRegistry(
        listOf(
            WebSearchTool(WebSearcher(httpClients)),
            ReadWebpageTool(PageFetcher(httpClients)),
            WeatherTool(httpClients),
            ReadScreenTool(screenContext),
            ShowUiTool(),
            RunCommandTool(shellAccess),
            RunPythonTool(python, shellAccess.workspace),
            ListFilesTool(deviceFiles),
            ReadFileTool(deviceFiles, attachments),
            WriteFileTool(deviceFiles),
            SendFilesTool(deviceFiles, attachments),
        ),
        sources = listOf(mcp),
    )

    init {
        applicationScope.launch {
            settings.settings.map { it.tools.mcpServers }.distinctUntilChanged().collect(mcp::sync)
        }
    }

    fun newVoiceRecorder() = VoiceRecorder(appContext)

    fun newSpeechSynthesizer(voice: VoiceSettings): SpeechSynthesizer {
        val provider = voice.speechProvider
        return if (voice.speechOutput == SpeechOutput.PROVIDER && provider != null) {
            ProviderSpeechSynthesizer(httpClients, provider, appContext.cacheDir)
        } else {
            SystemSpeechSynthesizer(appContext, voice.language)
        }
    }

    val approvals = ToolApprovals()

    val conversations = ConversationRunner(chats, settings, completions, systemPrompt, tools, attachments, approvals, applicationScope)

    private companion object {
        const val FILE_STAGING_DIR = "file-staging"
    }
}
