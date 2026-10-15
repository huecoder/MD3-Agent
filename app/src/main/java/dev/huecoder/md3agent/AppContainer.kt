package dev.huecoder.md3agent

import android.content.Context
import dev.huecoder.md3agent.data.files.ChatSentFiles
import dev.huecoder.md3agent.data.files.FileLibrary
import dev.huecoder.md3agent.data.chat.SearchHistory
import dev.huecoder.md3agent.ui.haptics.HapticEngine
import dev.huecoder.md3agent.assistant.ScreenContextStore
import dev.huecoder.md3agent.conversation.BackgroundWorkService
import dev.huecoder.md3agent.conversation.ConversationRunner
import dev.huecoder.md3agent.system.lifecycle.AppVisibility
import dev.huecoder.md3agent.conversation.GeneratedImages
import dev.huecoder.md3agent.conversation.UserPrompts
import dev.huecoder.md3agent.tools.AskUserTool
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.conversation.ToolApprovals
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.chat.ChatDrafts
import dev.huecoder.md3agent.tools.chats.ExportChatTool
import dev.huecoder.md3agent.tools.chats.ListChatsTool
import dev.huecoder.md3agent.tools.chats.ReadChatTool
import dev.huecoder.md3agent.tools.chats.SearchMessagesTool
import dev.huecoder.md3agent.data.updates.UpdateChecker
import dev.huecoder.md3agent.data.chat.ChatRepository
import dev.huecoder.md3agent.data.db.AppDatabase
import dev.huecoder.md3agent.data.memory.MemoryStore
import dev.huecoder.md3agent.llm.EmbeddingClient
import dev.huecoder.md3agent.tools.memory.MemoryDeleteTool
import dev.huecoder.md3agent.tools.memory.MemorySaveTool
import dev.huecoder.md3agent.tools.memory.MemorySearchTool
import dev.huecoder.md3agent.tools.memory.MemoryUpdateTool
import dev.huecoder.md3agent.data.settings.SecretCipher
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.llm.ChatCompletionClient
import dev.huecoder.md3agent.mcp.McpToolbox
import dev.huecoder.md3agent.mcp.auth.McpOAuth
import dev.huecoder.md3agent.mcp.auth.McpSignIn
import dev.huecoder.md3agent.data.settings.McpOAuthSession
import dev.huecoder.md3agent.llm.ModelCatalogClient
import dev.huecoder.md3agent.llm.TranscriptionClient
import dev.huecoder.md3agent.llm.VoiceListClient
import dev.huecoder.md3agent.net.HttpClientProvider
import dev.huecoder.md3agent.prompt.DeviceInfoProvider
import dev.huecoder.md3agent.prompt.SystemPromptRenderer
import dev.huecoder.md3agent.tools.ReadScreenTool
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.control.PhoneUseSounds
import dev.huecoder.md3agent.tools.phone.PhoneActionTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenTool
import dev.huecoder.md3agent.tools.phone.PhoneScreenshotTool
import dev.huecoder.md3agent.tools.ShowChartTool
import dev.huecoder.md3agent.tools.ShowDiagramTool
import dev.huecoder.md3agent.tools.ShowUiTool
import dev.huecoder.md3agent.tools.system.RunCommandTool
import dev.huecoder.md3agent.tools.system.RunPythonTool
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.system.shell.ShellAccess
import dev.huecoder.md3agent.tools.ToolRegistry
import dev.huecoder.md3agent.data.reminders.ReminderRepository
import dev.huecoder.md3agent.system.reminders.ClockApp
import dev.huecoder.md3agent.system.reminders.ReminderAlarms
import dev.huecoder.md3agent.tools.reminders.CancelReminderTool
import dev.huecoder.md3agent.tools.reminders.ListRemindersTool
import dev.huecoder.md3agent.tools.reminders.SetAlarmTool
import dev.huecoder.md3agent.tools.reminders.SetReminderTool
import dev.huecoder.md3agent.tools.reminders.SetTimerTool
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.system.python.PythonRuntime
import dev.huecoder.md3agent.tools.files.ListFilesTool
import dev.huecoder.md3agent.tools.files.ReadFileTool
import dev.huecoder.md3agent.tools.files.SendFilesTool
import dev.huecoder.md3agent.tools.files.WriteFileTool
import dev.huecoder.md3agent.tools.weather.WeatherTool
import dev.huecoder.md3agent.tools.web.PageFetcher
import dev.huecoder.md3agent.tools.web.ReadWebpageTool
import dev.huecoder.md3agent.tools.web.WebSearchTool
import dev.huecoder.md3agent.tools.web.WebSearcher
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.VoiceSettings
import dev.huecoder.md3agent.voice.ProviderSpeechSynthesizer
import dev.huecoder.md3agent.voice.SpeechSynthesizer
import dev.huecoder.md3agent.voice.SystemSpeechSynthesizer
import dev.huecoder.md3agent.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val visibility = AppVisibility()

    val settings = SettingsRepository(context, SecretCipher(), applicationScope)
    val hapticEngine = HapticEngine(appContext)
    val httpClients = HttpClientProvider(settings.networkSettings, applicationScope)

    private val database = AppDatabase.build(context)
    val attachments = AttachmentStore(context)
    val chats = ChatRepository(database.chatDao(), database.messageDao(), database.folderDao(), attachments, ChatDrafts(appContext))
    val searchHistory = SearchHistory(appContext)
    val reminders = ReminderRepository(database.reminderDao(), ReminderAlarms(appContext))
    val memories = MemoryStore(database.memoryDao(), EmbeddingClient(httpClients), settings.settings.map { it.tools.memory })

    val completions = ChatCompletionClient(httpClients)
    val modelCatalogClient = ModelCatalogClient(httpClients)
    val voiceList = VoiceListClient(httpClients)
    val modelCatalog = ModelCatalog(modelCatalogClient, File(appContext.filesDir, MODEL_CATALOG_DIR))
    val transcriptions = TranscriptionClient(httpClients)
    val systemPrompt = SystemPromptRenderer(DeviceInfoProvider(context))

    val screenContext = ScreenContextStore()
    val shellAccess = ShellAccess(appContext)
    private val uploads = File(appContext.filesDir, UPLOADS_DIR)
    val deviceFiles = DeviceFiles(shellAccess.workspace, attachments.directory, File(appContext.cacheDir, FILE_STAGING_DIR), uploads)
    val library = FileLibrary(appContext.contentResolver, uploads, shellAccess.workspace, ChatSentFiles(database.messageDao(), attachments))

    val python = PythonRuntime(appContext)
    val phoneControl = PhoneControl(appContext) {
        when {
            shellAccess.shizuku.status() == AccessStatus.READY -> shellAccess.shizuku
            shellAccess.root.status() == AccessStatus.READY -> shellAccess.root
            else -> null
        }
    }
    val phoneSounds = PhoneUseSounds(appContext)

    val prompts = UserPrompts()
    private val clockApp = ClockApp(appContext) { visibility.isVisible.value }
    private val mcpOAuth = McpOAuth(httpClients::current)
    val mcpSignIn = McpSignIn(mcpOAuth, settings)
    val mcp = McpToolbox(httpClients, attachments, prompts, mcpOAuth, ::saveMcpSession, applicationScope)

    val tools = ToolRegistry(
        listOf(
            WebSearchTool(WebSearcher(httpClients)),
            ReadWebpageTool(PageFetcher(httpClients)),
            WeatherTool(httpClients),
            ReadScreenTool(screenContext),
            PhoneScreenTool(phoneControl),
            PhoneScreenshotTool(phoneControl, attachments),
            PhoneActionTool(phoneControl),
            ShowUiTool(),
            ShowChartTool(),
            ShowDiagramTool(),
            AskUserTool(prompts),
            MemorySaveTool(memories),
            MemoryUpdateTool(memories),
            MemoryDeleteTool(memories),
            MemorySearchTool(memories),
            RunCommandTool(shellAccess),
            RunPythonTool(python, shellAccess.workspace),
            ListFilesTool(deviceFiles),
            ReadFileTool(deviceFiles, attachments),
            WriteFileTool(deviceFiles),
            SendFilesTool(deviceFiles, attachments),
            SetReminderTool(reminders),
            ListRemindersTool(reminders),
            CancelReminderTool(reminders),
            SetAlarmTool(clockApp),
            SetTimerTool(clockApp),
            ListChatsTool(chats),
            ReadChatTool(chats),
            SearchMessagesTool(chats),
            ExportChatTool(chats, deviceFiles),
        ),
        sources = listOf(mcp),
    )

    private suspend fun saveMcpSession(serverId: String, session: McpOAuthSession) = settings.updateTools { tools ->
        tools.copy(mcpServers = tools.mcpServers.map { if (it.id == serverId) it.copy(oauth = session) else it })
    }

    fun newVoiceRecorder() = VoiceRecorder(appContext)

    fun newSpeechSynthesizer(voice: VoiceSettings, agent: AgentProfile): SpeechSynthesizer {
        val provider = voice.speechProviderFor(agent.voice)
        return if (provider != null) {
            ProviderSpeechSynthesizer(httpClients, provider, appContext.cacheDir)
        } else {
            SystemSpeechSynthesizer(appContext, voice.language)
        }
    }

    val updates = UpdateChecker(httpClients, BuildConfig.VERSION_NAME)

    val approvals = ToolApprovals()

    val conversations = ConversationRunner(
        chats, settings, completions, systemPrompt, tools, attachments, approvals, prompts,
        modelCatalog, GeneratedImages(attachments, httpClients), memories, applicationScope,
    )

    init {
        applicationScope.launch {
            settings.settings.map { it.tools.mcpServers }.distinctUntilChanged().collect(mcp::sync)
        }
        applicationScope.launch {
            settings.settings.map { it.tools.phoneUse }.distinctUntilChanged().collect { phoneControl.session.settings.value = it }
        }
        applicationScope.launch {
            conversations.drafts.collect { drafts ->
                drafts.forEach { (chatId, draft) ->
                    val awaiting = draft.toolCalls.firstOrNull { it.id == draft.awaitingCallId }
                    if (awaiting != null && awaiting.name in PHONE_TOOLS) phoneControl.session.begin(chatId)
                }
                val active = phoneControl.session.chatId.value
                if (active != null && active !in drafts) phoneControl.session.end()
            }
        }
        applicationScope.launch {
            conversations.activity.map { it.isNotEmpty() }.distinctUntilChanged().collect { busy ->
                if (busy) withContext(Dispatchers.Main) { BackgroundWorkService.start(appContext) }
            }
        }
    }

    private companion object {
        val PHONE_TOOLS = setOf(PhoneScreenTool.NAME, PhoneScreenshotTool.NAME, PhoneActionTool.NAME)
        const val FILE_STAGING_DIR = "file-staging"
        const val UPLOADS_DIR = "uploads"
        const val MODEL_CATALOG_DIR = "model-catalog"
    }
}
