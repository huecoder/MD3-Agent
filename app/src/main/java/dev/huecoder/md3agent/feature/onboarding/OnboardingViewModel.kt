package dev.huecoder.md3agent.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.huecoder.md3agent.assistant.AssistantRole
import dev.huecoder.md3agent.data.chat.ChatTint
import dev.huecoder.md3agent.data.models.ModelCatalog
import dev.huecoder.md3agent.data.settings.ApiFormat
import dev.huecoder.md3agent.data.settings.ModelSelection
import dev.huecoder.md3agent.data.settings.ProviderProfile
import dev.huecoder.md3agent.llm.ProviderException
import dev.huecoder.md3agent.data.settings.AgentProfile
import dev.huecoder.md3agent.data.settings.AgentShape
import dev.huecoder.md3agent.data.settings.ModePreset
import dev.huecoder.md3agent.data.settings.ModeSettings
import dev.huecoder.md3agent.data.settings.SettingsRepository
import dev.huecoder.md3agent.feature.mode.ModeController
import dev.huecoder.md3agent.feature.settings.DevicePermission
import dev.huecoder.md3agent.ui.avatar.AgentLook
import dev.huecoder.md3agent.ui.locale.AppLanguage
import dev.huecoder.md3agent.ui.locale.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val steps: List<OnboardingStep>,
    val answers: SurveyAnswers,
    val recommended: ModePreset?,
    val preset: ModePreset?,
    val needs: SurveyNeeds,
    val language: AppLanguage,
    val cluster: List<AgentLook>,
    val agentName: String,
    val look: AgentLook,
    val missingPermissions: List<DevicePermission>,
    val providerName: String?,
    val model: ModelSelection?,
)

sealed interface ConnectResult {
    data class Verified(val models: Int) : ConnectResult

    data class Unverified(val reason: String) : ConnectResult
}

class OnboardingViewModel(
    private val context: Context,
    private val settings: SettingsRepository,
    private val modeController: ModeController,
    private val probe: PermissionProbe,
    private val catalog: ModelCatalog,
    private val retake: Boolean,
) : ViewModel() {
    private class Start(val hadProvider: Boolean, val assistantHeld: Boolean, val mode: ModeSettings, val agent: AgentProfile)

    private data class Draft(
        val answers: SurveyAnswers = SurveyAnswers(),
        val chosenPreset: ModePreset? = null,
        val language: AppLanguage,
        val visited: Set<OnboardingStep> = emptySet(),
        val agentName: String? = null,
        val agentShape: AgentShape? = null,
        val agentTint: ChatTint? = null,
    )

    private val draft = MutableStateFlow(Draft(language = AppLocale.current(context)))
    private val start = MutableStateFlow<Start?>(null)
    private val missing = MutableStateFlow(emptyList<DevicePermission>())
    private val shownPermissions = MutableStateFlow<List<DevicePermission>?>(null)
    private val cluster = AgentCluster.random()
    private var connectedProviderId: String? = null

    val state: StateFlow<OnboardingState?> = combine(draft, start, missing, settings.settings, shownPermissions) { current, begun, absent, all, shown ->
        begun ?: return@combine null
        val needs = Survey.needs(current.answers)
        val recommended = Survey.recommend(current.answers)
        val preset = current.chosenPreset ?: recommended ?: begun.mode.preset
        val facts = FlowFacts(
            preset = preset ?: ModePreset.DEVELOPER,
            needsProvider = !begun.hadProvider,
            needsAssistant = needs.assistant && !begun.assistantHeld,
            needsPermissions = absent.isNotEmpty(),
        )
        OnboardingState(
            steps = if (retake) OnboardingFlow.retake() else (OnboardingFlow.full(facts) + current.visited).distinct().sortedBy { it.ordinal },
            answers = current.answers,
            recommended = recommended,
            preset = preset,
            needs = needs,
            language = current.language,
            cluster = cluster,
            agentName = current.agentName ?: begun.agent.name,
            look = AgentLook(current.agentShape ?: begun.agent.shape, current.agentTint ?: begun.agent.tint),
            missingPermissions = shown ?: absent,
            providerName = all.providers.firstOrNull()?.name,
            model = all.activeSelection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            val all = settings.current()
            start.value = Start(all.providers.isNotEmpty(), AssistantRole.isHeld(context), all.mode, all.agents.active)
            refreshPermissions()
        }
    }

    fun answer(change: (SurveyAnswers) -> SurveyAnswers) {
        draft.update { it.copy(answers = change(it.answers)) }
        refreshPermissions()
    }

    fun choosePreset(preset: ModePreset) = draft.update { it.copy(chosenPreset = preset) }

    fun chooseLanguage(language: AppLanguage) = draft.update { it.copy(language = language) }

    fun renameAgent(name: String) = draft.update { it.copy(agentName = name) }

    fun pickShape(shape: AgentShape) = draft.update { it.copy(agentShape = shape) }

    fun pickTint(tint: ChatTint) = draft.update { it.copy(agentTint = tint) }

    fun markShown(step: OnboardingStep) {
        draft.update { it.copy(visited = it.visited + step) }
        if (step == OnboardingStep.PERMISSIONS && shownPermissions.value == null) shownPermissions.value = missing.value
    }

    fun selectModel(selection: ModelSelection) {
        viewModelScope.launch { settings.setActiveSelection(selection) }
    }

    suspend fun connectProvider(format: ApiFormat, name: String, key: String): ConnectResult {
        val id = connectedProviderId ?: settings.newProviderId().also { connectedProviderId = it }
        val profile = ProviderProfile(id = id, name = name, baseUrl = format.defaultBaseUrl, apiKey = key.trim(), format = format)
        settings.upsertProvider(profile)
        return try {
            ConnectResult.Verified(catalog.refresh(profile).size)
        } catch (e: ProviderException) {
            ConnectResult.Unverified(e.message.orEmpty())
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch { missing.value = probe.missing(Survey.needs(draft.value.answers)) }
    }

    suspend fun complete(skipped: Boolean) {
        val current = state.value
        val chosen = current?.preset
        if (skipped || current == null || chosen == null) {
            modeController.update { it.copy(onboarded = true) }
            return
        }
        modeController.update { it.with(chosen, phoneControl = current.needs.phoneControl).copy(onboarded = true) }
        if (retake || OnboardingStep.FIRST_AGENT !in draft.value.visited) return
        settings.updateAgents { agents ->
            val active = agents.active
            agents.upsert(active.copy(name = current.agentName.trim().ifEmpty { active.name }, shape = current.look.shape, tint = current.look.tint))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
