package com.mwagent

import android.content.Context
import com.mwagent.data.files.ChatSentFiles
import com.mwagent.data.files.FileLibrary
import com.mwagent.data.chat.SearchHistory
import com.mwagent.ui.haptics.HapticEngine
import com.mwagent.assistant.ScreenContextStore
import com.mwagent.conversation.BackgroundWorkService
import com.mwagent.conversation.ConversationRunner
import com.mwagent.system.lifecycle.AppVisibility
import com.mwagent.conversation.GeneratedImages
import com.mwagent.conversation.UserPrompts
import com.mwagent.tools.AskUserTool
import com.mwagent.data.models.ModelCatalog
import com.mwagent.conversation.ToolApprovals
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.chat.ChatDrafts
import com.mwagent.tools.chats.ExportChatTool
import com.mwagent.tools.chats.ListChatsTool
import com.mwagent.tools.chats.ReadChatTool
import com.mwagent.tools.chats.SearchMessagesTool
import com.mwagent.data.updates.UpdateChecker
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.db.AppDatabase
import com.mwagent.data.memory.MemoryStore
import com.mwagent.llm.EmbeddingClient
import com.mwagent.tools.memory.MemoryDeleteTool
import com.mwagent.tools.memory.MemorySaveTool
import com.mwagent.tools.memory.MemorySearchTool
import com.mwagent.tools.memory.MemoryUpdateTool
import com.mwagent.data.settings.SecretCipher
import com.mwagent.data.settings.SettingsRepository
import com.mwagent.llm.ChatCompletionClient
import com.mwagent.llm.ToolCall
import com.mwagent.mcp.McpToolbox
import com.mwagent.mcp.auth.McpOAuth
import com.mwagent.mcp.auth.McpSignIn
import com.mwagent.data.settings.McpOAuthSession
import com.mwagent.llm.ModelCatalogClient
import com.mwagent.llm.TranscriptionClient
import com.mwagent.llm.VoiceListClient
import com.mwagent.net.HttpClientProvider
import com.mwagent.prompt.DeviceInfoProvider
import com.mwagent.prompt.SystemPromptRenderer
import com.mwagent.tools.ReadScreenTool
import com.mwagent.system.control.PhoneControl
import com.mwagent.system.control.PhoneUseSounds
import com.mwagent.tools.phone.PhoneActionTool
import com.mwagent.tools.phone.PhoneScreenTool
import com.mwagent.tools.phone.PhoneScreenshotTool
import com.mwagent.tools.ShowChartTool
import com.mwagent.tools.ShowDiagramTool
import com.mwagent.tools.ShowUiTool
import com.mwagent.tools.system.RunCommandTool
import com.mwagent.tools.system.RunPythonTool
import com.mwagent.system.shell.AccessStatus
import com.mwagent.system.shell.ShellAccess
import com.mwagent.tools.ToolRegistry
import com.mwagent.data.reminders.ReminderRepository
import com.mwagent.system.reminders.ClockApp
import com.mwagent.system.reminders.ReminderAlarms
import com.mwagent.tools.reminders.CancelReminderTool
import com.mwagent.tools.reminders.ListRemindersTool
import com.mwagent.tools.reminders.SetAlarmTool
import com.mwagent.tools.reminders.SetReminderTool
import com.mwagent.tools.reminders.SetTimerTool
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.location.DeviceLocation
import com.mwagent.system.python.PythonRuntime
import com.mwagent.tools.files.ListFilesTool
import com.mwagent.tools.files.ReadFileTool
import com.mwagent.tools.files.SendFilesTool
import com.mwagent.tools.files.WriteFileTool
import com.mwagent.tools.location.LocationTool
import com.mwagent.tools.weather.WeatherTool
import com.mwagent.tools.web.PageFetcher
import com.mwagent.tools.web.ReadWebpageTool
import com.mwagent.tools.web.WebSearchTool
import com.mwagent.tools.web.WebSearcher
import com.mwagent.data.settings.AgentProfile
import com.mwagent.data.settings.VoiceSettings
import com.mwagent.voice.ProviderSpeechSynthesizer
import com.mwagent.voice.SpeechSynthesizer
import com.mwagent.voice.SystemSpeechSynthesizer
import com.mwagent.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    val deviceLocation = DeviceLocation(appContext)

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
            LocationTool(deviceLocation),
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
            RunPythonTool(python, shellAccess.workspace, phoneControl),
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
                    if (awaiting != null && awaiting.drivesPhone()) phoneControl.session.begin(chatId)
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

    private fun ToolCall.drivesPhone(): Boolean = name in PHONE_TOOLS || (name == RunPythonTool.NAME && runCatching { RunPythonTool.usesPhone(Json.parseToJsonElement(arguments).jsonObject) }.getOrDefault(false))

    private companion object {
        val PHONE_TOOLS = setOf(PhoneScreenTool.NAME, PhoneScreenshotTool.NAME, PhoneActionTool.NAME)
        const val FILE_STAGING_DIR = "file-staging"
        const val UPLOADS_DIR = "uploads"
        const val MODEL_CATALOG_DIR = "model-catalog"
    }
}
