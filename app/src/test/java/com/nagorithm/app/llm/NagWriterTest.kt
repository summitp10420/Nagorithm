package com.nagorithm.app.llm

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

    @Test
    fun `every figure in the prompt is one the answer may quote`() {
        val v = verdict(VerdictState.IDLE)
        val request = NagPrompt.build(v, NagTemplates.render(v), NagTone.DRY)

        // Otherwise the guard would reject a faithful rewrite for repeating a number the prompt
        // itself put in front of the model.
        val supplied = request.supplied.flatMap(NagGuard::numbersIn).toSet()
        assertTrue(supplied.contains(412.0))
    }
}
