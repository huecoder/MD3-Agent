package dev.huecoder.md3agent.feature.onboarding

import dev.huecoder.md3agent.data.settings.ModePreset

enum class OnboardingStep(val calm: Boolean) {
    WELCOME(calm = false),
    ABOUT(calm = true),
    GOALS(calm = true),
    EXPERIENCE(calm = true),
    PLACES(calm = true),
    MODE(calm = true),
    AGENTS(calm = false),
    ABILITIES(calm = true),
    PROVIDER(calm = true),
    ASSISTANT(calm = true),
    PERMISSIONS(calm = true),
    FIRST_AGENT(calm = true),
    DONE(calm = true),
}

data class FlowFacts(
    val preset: ModePreset,
    val needsProvider: Boolean,
    val needsAssistant: Boolean,
    val needsPermissions: Boolean,
)

object OnboardingFlow {
    private val SURVEY = listOf(OnboardingStep.GOALS, OnboardingStep.EXPERIENCE, OnboardingStep.PLACES, OnboardingStep.MODE)

    fun retake(): List<OnboardingStep> = SURVEY

    fun full(facts: FlowFacts): List<OnboardingStep> {
        val withAgents = facts.preset != ModePreset.EVERYDAY
        return buildList {
            add(OnboardingStep.WELCOME)
            add(OnboardingStep.ABOUT)
            addAll(SURVEY)
            if (withAgents) {
                add(OnboardingStep.AGENTS)
                add(OnboardingStep.ABILITIES)
            }
            if (facts.needsProvider) add(OnboardingStep.PROVIDER)
            if (facts.needsAssistant) add(OnboardingStep.ASSISTANT)
            if (facts.needsPermissions) add(OnboardingStep.PERMISSIONS)
            if (withAgents) add(OnboardingStep.FIRST_AGENT)
            add(OnboardingStep.DONE)
        }
    }
}
