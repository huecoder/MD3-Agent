package dev.huecoder.md3agent.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.huecoder.md3agent.llm.models.ReasoningEffort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.util.UUID

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, private val cipher: SecretCipher) {
    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val providerListSerializer = ListSerializer(ProviderProfile.serializer())

    val settings: Flow<AppSettings> = store.data.map(::toAppSettings).distinctUntilChanged()

    val networkSettings: Flow<NetworkSettings> = settings.map { it.network }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun upsertProvider(provider: ProviderProfile) = editProviders { providers ->
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index == -1) providers + provider else providers.toMutableList().also { it[index] = provider }
    }

    suspend fun deleteProvider(providerId: String) {
        editProviders { providers -> providers.filterNot { it.id == providerId } }
        store.edit { prefs ->
            if (prefs[Keys.ACTIVE_PROVIDER] == providerId) {
                prefs.remove(Keys.ACTIVE_PROVIDER)
                prefs.remove(Keys.ACTIVE_MODEL)
            }
        }
    }

    suspend fun setActiveSelection(selection: ModelSelection) = store.edit { prefs ->
        prefs[Keys.ACTIVE_PROVIDER] = selection.providerId
        prefs[Keys.ACTIVE_MODEL] = selection.modelId
        val effort = selection.reasoningEffort
        if (effort == null) prefs.remove(Keys.ACTIVE_REASONING) else prefs[Keys.ACTIVE_REASONING] = effort.wireName
    }

    suspend fun updateAgent(agent: AgentProfile) = updateAgent { agent }

    suspend fun updateAgent(transform: (AgentProfile) -> AgentProfile) = store.edit { prefs ->
        val current = decodeOrDefault(prefs[Keys.AGENT], AgentProfile.serializer(), AgentProfile()).migrated()
        prefs[Keys.AGENT] = json.encodeToString(AgentProfile.serializer(), transform(current))
    }

    suspend fun updateNetwork(network: NetworkSettings) = store.edit { prefs ->
        prefs[Keys.NETWORK] = json.encodeToString(NetworkSettings.serializer(), network)
    }

    suspend fun updateTools(transform: (ToolSettings) -> ToolSettings) = store.edit { prefs ->
        val updated = transform(decodeTools(prefs[Keys.TOOLS_ENCRYPTED]))
        prefs[Keys.TOOLS_ENCRYPTED] = cipher.encrypt(json.encodeToString(ToolSettings.serializer(), updated))
    }

    suspend fun updateChat(transform: (ChatSettings) -> ChatSettings) = store.edit { prefs ->
        val current = decodeOrDefault(prefs[Keys.CHAT], ChatSettings.serializer(), ChatSettings())
        prefs[Keys.CHAT] = json.encodeToString(ChatSettings.serializer(), transform(current))
    }

    suspend fun updateInterface(transform: (InterfaceSettings) -> InterfaceSettings) = store.edit { prefs ->
        val current = decodeOrDefault(prefs[Keys.INTERFACE], InterfaceSettings.serializer(), InterfaceSettings())
        prefs[Keys.INTERFACE] = json.encodeToString(InterfaceSettings.serializer(), transform(current))
    }

    suspend fun updateVoice(transform: (VoiceSettings) -> VoiceSettings) = store.edit { prefs ->
        val updated = transform(decodeVoice(prefs))
        prefs[Keys.VOICE_ENCRYPTED] = cipher.encrypt(json.encodeToString(VoiceSettings.serializer(), updated))
        prefs.remove(Keys.LEGACY_SPEECH)
    }

    fun newProviderId(): String = UUID.randomUUID().toString()

    private suspend fun editProviders(transform: (List<ProviderProfile>) -> List<ProviderProfile>) {
        store.edit { prefs ->
            val updated = transform(decodeProviders(prefs[Keys.PROVIDERS_ENCRYPTED]))
            prefs[Keys.PROVIDERS_ENCRYPTED] = cipher.encrypt(json.encodeToString(providerListSerializer, updated))
        }
    }

    private fun toAppSettings(prefs: Preferences): AppSettings {
        val providers = decodeProviders(prefs[Keys.PROVIDERS_ENCRYPTED])
        val activeProvider = prefs[Keys.ACTIVE_PROVIDER]
        val activeModel = prefs[Keys.ACTIVE_MODEL]
        val selection = if (activeProvider != null && activeModel != null && providers.any { it.id == activeProvider }) {
            ModelSelection(activeProvider, activeModel, prefs[Keys.ACTIVE_REASONING]?.let(ReasoningEffort::fromWire))
        } else {
            providers.firstOrNull { it.defaultModel.isNotBlank() }?.let { ModelSelection(it.id, it.defaultModel) }
        }
        return AppSettings(
            providers = providers,
            activeSelection = selection,
            agent = decodeOrDefault(prefs[Keys.AGENT], AgentProfile.serializer(), AgentProfile()).migrated(),
            network = decodeOrDefault(prefs[Keys.NETWORK], NetworkSettings.serializer(), NetworkSettings()),
            tools = decodeTools(prefs[Keys.TOOLS_ENCRYPTED]),
            chat = decodeOrDefault(prefs[Keys.CHAT], ChatSettings.serializer(), ChatSettings()),
            voice = decodeVoice(prefs, providers, activeProvider),
            ui = decodeOrDefault(prefs[Keys.INTERFACE], InterfaceSettings.serializer(), InterfaceSettings()),
        )
    }

    private fun decodeProviders(encrypted: String?): List<ProviderProfile> {
        if (encrypted == null) return emptyList()
        return try {
            json.decodeFromString(providerListSerializer, cipher.decrypt(encrypted))
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Provider profiles could not be decrypted, starting empty", e)
            emptyList()
        } catch (e: SerializationException) {
            Log.e(TAG, "Provider profiles are corrupted, starting empty", e)
            emptyList()
        }
    }

    private fun decodeVoice(
        prefs: Preferences,
        providers: List<ProviderProfile> = decodeProviders(prefs[Keys.PROVIDERS_ENCRYPTED]),
        activeProviderId: String? = prefs[Keys.ACTIVE_PROVIDER],
    ): VoiceSettings {
        val encrypted = prefs[Keys.VOICE_ENCRYPTED]
        if (encrypted == null) {
            val legacy = prefs[Keys.LEGACY_SPEECH] ?: return VoiceSettings()
            return LegacySpeechMigration.toVoice(
                decodeOrDefault(legacy, LegacySpeechSettings.serializer(), LegacySpeechSettings()),
                providers,
                activeProviderId,
            )
        }
        val plain = try {
            cipher.decrypt(encrypted)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Voice settings could not be decrypted, using defaults", e)
            return VoiceSettings()
        }
        return decodeOrDefault(plain, VoiceSettings.serializer(), VoiceSettings())
    }

    private fun decodeTools(encrypted: String?): ToolSettings {
        if (encrypted == null) return ToolSettings()
        val plain = try {
            cipher.decrypt(encrypted)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Tool settings could not be decrypted, using defaults", e)
            return ToolSettings()
        }
        return decodeOrDefault(plain, ToolSettings.serializer(), ToolSettings()).migrated()
    }

    private fun <T> decodeOrDefault(raw: String?, serializer: KSerializer<T>, default: T): T {
        if (raw == null) return default
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            Log.e(TAG, "Setting ${serializer.descriptor.serialName} is corrupted, using defaults", e)
            default
        }
    }

    private object Keys {
        val PROVIDERS_ENCRYPTED = stringPreferencesKey("providers_encrypted")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ACTIVE_MODEL = stringPreferencesKey("active_model")
        val ACTIVE_REASONING = stringPreferencesKey("active_reasoning")
        val AGENT = stringPreferencesKey("agent")
        val NETWORK = stringPreferencesKey("network")
        val TOOLS_ENCRYPTED = stringPreferencesKey("tools_encrypted")
        val CHAT = stringPreferencesKey("chat")
        val INTERFACE = stringPreferencesKey("interface")
        val LEGACY_SPEECH = stringPreferencesKey("speech")
        val VOICE_ENCRYPTED = stringPreferencesKey("voice_encrypted")
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
