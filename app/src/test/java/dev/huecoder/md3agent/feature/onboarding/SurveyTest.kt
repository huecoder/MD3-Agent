package dev.huecoder.md3agent.feature.onboarding

import dev.huecoder.md3agent.data.settings.ModePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyTest {
    @Test
    fun `no answers give no recommendation`() {
        assertNull(Survey.recommend(SurveyAnswers()))
        assertNull(Survey.recommend(SurveyAnswers(places = setOf(Place.CHAT))))
    }

    @Test
    fun `everyday tasks without experience stay everyday`() {
        val answers = SurveyAnswers(goals = setOf(Goal.ASK, Goal.REMINDERS), experience = setOf(Experience.NONE))
        assertEquals(ModePreset.EVERYDAY, Survey.recommend(answers))
    }

    @Test
    fun `power tasks alone point to the developer mode`() {
        val answers = SurveyAnswers(goals = setOf(Goal.FILES_CODE, Goal.AUTOMATE), experience = setOf(Experience.TERMINAL))
        assertEquals(ModePreset.DEVELOPER, Survey.recommend(answers))
    }

    @Test
    fun `both kinds of tasks give the middle mode`() {
        val answers = SurveyAnswers(goals = setOf(Goal.ASK, Goal.FILES_CODE))
        assertEquals(ModePreset.BOTH, Survey.recommend(answers))
    }

    @Test
    fun `experience alone without any stated task gives the middle mode`() {
        assertEquals(ModePreset.BOTH, Survey.recommend(SurveyAnswers(experience = setOf(Experience.API_KEY))))
    }

    @Test
    fun `none of these clears the other experience answers and the other way round`() {
        val picked = SurveyAnswers().toggle(Experience.API_KEY).toggle(Experience.TERMINAL)
        assertEquals(setOf(Experience.API_KEY, Experience.TERMINAL), picked.experience)
        assertEquals(setOf(Experience.NONE), picked.toggle(Experience.NONE).experience)
        assertEquals(setOf(Experience.SIDELOAD), picked.toggle(Experience.NONE).toggle(Experience.SIDELOAD).experience)
    }

    @Test
    fun `a choice toggles off when picked twice`() {
        val twice = SurveyAnswers().toggle(Goal.ASK).toggle(Goal.ASK).toggle(Place.VOICE).toggle(Place.VOICE)
        assertEquals(SurveyAnswers(), twice)
    }

    @Test
    fun `needs follow the answers`() {
        val none = Survey.needs(SurveyAnswers())
        assertFalse(none.phoneControl || none.assistant || none.allFiles || none.termux || none.microphone)
        assertTrue(none.keyHelpInDetail)

        val busy = Survey.needs(
            SurveyAnswers(
                goals = setOf(Goal.VOICE_CONTROL, Goal.FILES_CODE),
                experience = setOf(Experience.API_KEY, Experience.TERMINAL),
                places = setOf(Place.OVERLAY),
            ),
        )
        assertTrue(busy.phoneControl && busy.assistant && busy.allFiles && busy.termux && busy.microphone)
        assertFalse(busy.keyHelpInDetail)
    }

    @Test
    fun `looking at the screen asks for the assistant even without the overlay answer`() {
        assertTrue(Survey.needs(SurveyAnswers(goals = setOf(Goal.SCREEN))).assistant)
    }
}
