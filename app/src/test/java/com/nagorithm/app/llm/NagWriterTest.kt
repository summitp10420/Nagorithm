package com.nagorithm.app.llm

import com.nagorithm.app.data.Goal
import com.nagorithm.app.data.GoalDimension
import com.nagorithm.app.effort.EffortSnapshot
import com.nagorithm.app.verdict.DetrainingBand
import com.nagorithm.app.verdict.NagSource
import com.nagorithm.app.verdict.NagTemplates
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.Verdict
import com.nagorithm.app.verdict.VerdictState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NagWriterTest {

    private fun verdict(state: VerdictState) = Verdict(
        state = state,
        computedAt = Instant.now(),
        snapshot = EffortSnapshot.from(emptyList(), Instant.now()),
        goal = null,
        goalProgress = null,
        news = emptyList(),
        headline = null,
        efficiency = null,
        detraining = DetrainingBand.DRIFTING,
        daysSinceLastActivity = 9,
        acwr = null,
        reasons = listOf("Nothing in the last 7 days.", "28-day load is 412 HRSS."),
    )

    @Test
    fun `without a key the template still goes out`() = runBlocking {
        val outcome = NagWriter(client = null).write(verdict(VerdictState.IDLE), NagTone.BLUNT)

        assertEquals(NagSource.TEMPLATE, outcome.nag.source)
        assertTrue(outcome.nag.body.isNotBlank())
        assertTrue(outcome.note.contains("No API key"))
    }

    @Test
    fun `the prompt carries the verdict and none of the activity`() {
        val v = verdict(VerdictState.IDLE)
        val request = NagPrompt.build(v, NagTemplates.render(v), NagTone.SNARKY)

        assertTrue(request.prompt.contains("IDLE"))
        assertTrue(request.prompt.contains("28-day load is 412 HRSS."))
        assertTrue(request.prompt.contains(NagTone.SNARKY.guidance))

        // The reasons are a summary the engine chose to publish. Anything resembling a raw feed —
        // timestamps, record ids, per-session rows — would mean the model is being handed the
        // health data the app promises never to send anywhere.
        assertFalse(request.prompt.contains("recordId"))
        assertFalse(request.prompt.contains("EpochMillis"))
    }

    /**
     * The figures go in so the model can reason, and are absent from the quotable list so it cannot
     * recite them. Both halves matter: drop the first and it has nothing to observe, drop the second
     * and the load figure comes straight back out in the message.
     */
    @Test
    fun `the app's own figures are shown to the model but never made quotable`() {
        val v = verdict(VerdictState.IDLE)
        val request = NagPrompt.build(v, NagTemplates.render(v), NagTone.DRY)

        assertTrue(request.prompt.contains("412"))
        assertFalse(request.quotable.any { it.contains("412") })
        assertFalse(NagGuard.check("You are carrying 412 HRSS a week.", request.quotable).accepted)
    }

    /** A goal is written by the user, so its digits have to stay sayable. */
    @Test
    fun `the goal is the one thing whose figures may be repeated`() {
        val v = verdict(VerdictState.IDLE).copy(
            goal = Goal(
                text = "sub-3 marathon",
                dimension = GoalDimension.VOLUME,
                createdAtEpochMillis = 0L,
            ),
        )
        val request = NagPrompt.build(v, NagTemplates.render(v), NagTone.DRY)

        assertTrue(request.quotable.any { it.contains("sub-3") })
    }

    /**
     * Every verdict gets the purpose and an instruction of its own, including the quiet ones.
     *
     * The rules end by telling the model to cut any sentence that does not do one of the listed
     * jobs. A state that arrived without its own instruction would be left with only that, and on
     * a rest day or a broken sync the correct response to it is an empty message.
     */
    @Test
    fun `no verdict reaches the model without a purpose and an instruction of its own`() {
        VerdictState.entries.forEach { state ->
            val v = verdict(state)
            val prompt = NagPrompt.build(v, NagTemplates.render(v), NagTone.DRY).prompt

            assertTrue("$state lost the purpose", prompt.contains("WHY THIS MESSAGE EXISTS"))

            val instruction = prompt
                .substringAfter("${state.name} — ", missingDelimiterValue = "")
                .substringBefore('\n')
            assertTrue("$state was handed: \"$instruction\"", instruction.length > 40)
        }
    }

    /** Every template is read by a human, so none of them may recite arithmetic either. */
    @Test
    fun `no template contains a digit`() {
        VerdictState.entries.forEach { state ->
            val nag = NagTemplates.render(verdict(state))
            val text = "${nag.headline} ${nag.body}"

            assertFalse("$state said: $text", text.any(Char::isDigit))
        }
    }
}
