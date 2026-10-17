package com.mwagent.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException

class SettingsRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class CountingCipher : TextCipher {
        var decryptions = 0
        var broken = false

        override fun encrypt(plainText: String) = PREFIX + plainText

        override fun decrypt(encoded: String): String {
            decryptions++
            if (broken) throw GeneralSecurityException("keystore unavailable")
            return encoded.removePrefix(PREFIX)
        }

        private companion object {
            const val PREFIX = "sealed:"
        }
    }

    private val cipher = CountingCipher()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(temp.root, "settings.preferences_pb")
    }

    private fun TestScope.repository(store: DataStore<Preferences> = store()) = SettingsRepository(store, cipher, backgroundScope)

    private val provider = ProviderProfile(id = "p1", name = "Local", baseUrl = "http://localhost", apiKey = "secret")

    @Test
    fun `many subscribers cost one decryption per stored blob`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(dimScreen = true)) }
        repository.updateVoice { it.copy(language = "ru") }
        runCurrent()
        cipher.decryptions = 0

        val subscribers = List(SUBSCRIBERS) { backgroundScope.launch { repository.settings.first() } }
        runCurrent()
        subscribers.forEach { it.join() }

        assertEquals(0, cipher.decryptions)
        assertEquals(listOf(provider), repository.settings.first().providers)
    }

    @Test
    fun `changing chat settings decrypts nothing`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(dimScreen = true)) }
        runCurrent()
        cipher.decryptions = 0

        repository.updateChat { it.copy(showTokenUsage = true) }
        runCurrent()

        assertEquals(0, cipher.decryptions)
        assertTrue(repository.settings.first().chat.showTokenUsage)
    }

    @Test
    fun `changing tools decrypts only the tools blob once`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(dimScreen = false)) }
        runCurrent()
        cipher.decryptions = 0

        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(dimScreen = true)) }
        runCurrent()
        repeat(SUBSCRIBERS) { repository.settings.first() }

        assertTrue("decrypted ${cipher.decryptions} times", cipher.decryptions <= 1)
    }

    @Test
    fun `current reads the value that was just written`() = runTest {
        val repository = repository()

        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(keepAwake = false)) }

        assertFalse(repository.current().tools.phoneUse.keepAwake)
        repository.updateTools { it.copy(phoneUse = it.phoneUse.copy(keepAwake = true)) }
        assertTrue(repository.current().tools.phoneUse.keepAwake)
    }

    @Test
    fun `a failed decryption is retried instead of remembered`() = runTest {
        val store = store()
        val writer = repository(store)
        writer.upsertProvider(provider)
        runCurrent()

        cipher.broken = true
        val reader = repository(store)
        assertTrue(reader.current().providers.isEmpty())

        cipher.broken = false
        assertEquals(listOf(provider), reader.current().providers)
    }

    @Test
    fun `moving the phone panel touches no encrypted blob`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        runCurrent()
        val before = repository.settings.first()
        cipher.decryptions = 0

        repository.updatePhonePanel { it.copy(collapsed = true).withSpot(landscape = true, PanelSpot(12, 34)) }
        runCurrent()

        val panel = repository.phonePanel.first()
        assertTrue(panel.collapsed)
        assertEquals(PanelSpot(12, 34), panel.spot(landscape = true))
        assertEquals(null, panel.spot(landscape = false))
        assertEquals(0, cipher.decryptions)
        assertEquals(before, repository.settings.first())
    }

    @Test
    fun `a provider saved with a model list by an older build still loads`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey(PROVIDERS_KEY)] = cipher.encrypt(PROVIDERS_WITH_MODEL_LIST) }

        assertEquals(listOf(provider), repository(store).current().providers)
    }

    @Test
    fun `a provider alone selects no model`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)

        assertNull(repository.current().activeSelection)

        repository.setActiveSelection(ModelSelection("p1", "m"))
        assertEquals(ModelSelection("p1", "m"), repository.current().activeSelection)
    }

    @Test
    fun `deleting the active provider leaves no selection`() = runTest {
        val repository = repository()
        repository.upsertProvider(provider)
        repository.upsertProvider(provider.copy(id = "p2", name = "Other"))
        repository.setActiveSelection(ModelSelection("p1", "m"))

        repository.deleteProvider("p1")

        assertNull(repository.current().activeSelection)
        assertEquals(listOf("p2"), repository.current().providers.map { it.id })
    }

    private companion object {
        const val SUBSCRIBERS = 20
        const val PROVIDERS_KEY = "providers_encrypted"
        const val PROVIDERS_WITH_MODEL_LIST =
            """[{"id":"p1","name":"Local","baseUrl":"http://localhost","apiKey":"secret","models":["m","n"],"defaultModel":"m"}]"""
    }
}
