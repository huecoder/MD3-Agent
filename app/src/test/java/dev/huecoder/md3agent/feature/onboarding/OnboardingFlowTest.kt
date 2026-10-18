package dev.huecoder.md3agent.feature.onboarding

import dev.huecoder.md3agent.data.settings.ModePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private fun facts(preset: ModePreset, provider: Boolean = false, assistant: Boolean = false, permissions: Boolean = false) =
        FlowFacts(preset, provider, assistant, permissions)

    @Test
    fun `the short path has only what an everyday user needs`() {
        val steps = OnboardingFlow.full(facts(ModePreset.EVERYDAY, provider = true))
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.ABOUT, OnboardingStep.GOALS, OnboardingStep.EXPERIENCE, OnboardingStep.PLACES,
                OnboardingStep.MODE, OnboardingStep.PROVIDER, OnboardingStep.DONE,
            ),
            steps,
        )
    }

    @Test
    fun `agents abilities and the first agent appear for the other modes`() {
        ModePreset.entries.filter { it != ModePreset.EVERYDAY }.forEach { preset ->
            val steps = OnboardingFlow.full(facts(preset))
            assertTrue(OnboardingStep.AGENTS in steps && OnboardingStep.ABILITIES in steps && OnboardingStep.FIRST_AGENT in steps)
            assertTrue(steps.indexOf(OnboardingStep.FIRST_AGENT) < steps.indexOf(OnboardingStep.DONE))
        }
    }

    @Test
    fun `provider assistant and permissions are shown only when needed`() {
        val bare = OnboardingFlow.full(facts(ModePreset.BOTH))
        assertFalse(OnboardingStep.PROVIDER in bare || OnboardingStep.ASSISTANT in bare || OnboardingStep.PERMISSIONS in bare)
        val needy = OnboardingFlow.full(facts(ModePreset.BOTH, provider = true, assistant = true, permissions = true))
        assertTrue(OnboardingStep.PROVIDER in needy && OnboardingStep.ASSISTANT in needy && OnboardingStep.PERMISSIONS in needy)
    }

    @Test
    fun `the order follows the plan`() {
        val steps = OnboardingFlow.full(facts(ModePreset.DEVELOPER, provider = true, assistant = true, permissions = true))
        assertEquals(steps, steps.sortedBy { it.ordinal })
        assertEquals(OnboardingStep.WELCOME, steps.first())
        assertEquals(OnboardingStep.DONE, steps.last())
    }

    @Test
    fun `retaking the quiz only asks the questions and shows the result`() {
        assertEquals(
            listOf(OnboardingStep.GOALS, OnboardingStep.EXPERIENCE, OnboardingStep.PLACES, OnboardingStep.MODE),
            OnboardingFlow.retake(),
        )
    }
}
