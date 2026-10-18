package dev.huecoder.md3agent.feature.onboarding

import dev.huecoder.md3agent.data.settings.ModePreset

enum class Goal(val signal: Signal) {
    ASK(Signal.EVERYDAY),
    SCREEN(Signal.EVERYDAY),
    REMINDERS(Signal.EVERYDAY),
    VOICE_CONTROL(Signal.EVERYDAY),
    FILES_CODE(Signal.POWER),
    AUTOMATE(Signal.POWER),
}

enum class Experience { API_KEY, TERMINAL, SIDELOAD, NONE }

enum class Place { CHAT, OVERLAY, VOICE }

enum class Signal { EVERYDAY, POWER }

data class SurveyAnswers(
    val goals: Set<Goal> = emptySet(),
    val experience: Set<Experience> = emptySet(),
    val places: Set<Place> = emptySet(),
) {
    fun toggle(goal: Goal) = copy(goals = goals.toggled(goal))

    fun toggle(item: Experience): SurveyAnswers {
        val next = if (item == Experience.NONE) {
            if (Experience.NONE in experience) emptySet() else setOf(Experience.NONE)
        } else {
            experience.toggled(item) - Experience.NONE
        }
        return copy(experience = next)
    }

    fun toggle(place: Place) = copy(places = places.toggled(place))

    private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item
}

data class SurveyNeeds(
    val phoneControl: Boolean,
    val assistant: Boolean,
    val allFiles: Boolean,
    val termux: Boolean,
    val microphone: Boolean,
    val keyHelpInDetail: Boolean,
)

object Survey {
    fun recommend(answers: SurveyAnswers): ModePreset? {
        if (answers.goals.isEmpty() && answers.experience.isEmpty()) return null
        val power = answers.goals.count { it.signal == Signal.POWER } + answers.experience.count { it != Experience.NONE }
        val everyday = answers.goals.count { it.signal == Signal.EVERYDAY }
        return when {
            power == 0 -> ModePreset.EVERYDAY
            everyday == 0 && answers.goals.isNotEmpty() -> ModePreset.DEVELOPER
            else -> ModePreset.BOTH
        }
    }

    fun needs(answers: SurveyAnswers) = SurveyNeeds(
        phoneControl = Goal.VOICE_CONTROL in answers.goals,
        assistant = Goal.SCREEN in answers.goals || Place.OVERLAY in answers.places,
        allFiles = Goal.FILES_CODE in answers.goals || Goal.AUTOMATE in answers.goals,
        termux = Experience.TERMINAL in answers.experience,
        microphone = Place.VOICE in answers.places || Goal.VOICE_CONTROL in answers.goals,
        keyHelpInDetail = Experience.API_KEY !in answers.experience,
    )
}
