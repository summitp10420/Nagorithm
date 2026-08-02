package com.nagorithm.app.llm

import com.nagorithm.app.verdict.Nag
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.Verdict
import com.nagorithm.app.verdict.VerdictState

/** A prompt and the figures the answer is allowed to contain. */
data class NagRequest(val prompt: String, val supplied: List<String>)

/**
 * Builds the only thing the model ever sees.
 *
 * It receives the conclusion and the figures behind it — never the activity history. That is a
 * privacy property, since a month of timestamped locations and heart rates stays on the device,
 * and a correctness one: a model given only a finished verdict has nothing to reinterpret into a
 * different verdict. It cannot decide the user is doing well, because it was not given the
 * material an opinion could be formed from.
 */
object NagPrompt {

    fun build(verdict: Verdict, template: Nag, tone: NagTone): NagRequest {
        val facts = verdict.reasons

        val prompt = buildString {
            appendLine(
                "You are the voice of a training app that has already decided what to say. Your " +
                    "only job is to say it better.",
            )
            appendLine()
            appendLine("TONE: ${tone.guidance}")
            appendLine()
            appendLine("THE VERDICT: ${verdict.state.name} — ${meaning(verdict.state)}")
            appendLine()

            verdict.goal?.let { appendLine("THEIR GOAL, IN THEIR WORDS: \"${it.text}\"") }

            appendLine()
            appendLine("THE ONLY FACTS YOU HAVE, AND THE ONLY NUMBERS YOU MAY USE:")
            facts.forEach { appendLine("- $it") }

            appendLine()
            appendLine("A PLAIN VERSION OF THE MESSAGE, FOR CONTENT NOT STYLE:")
            appendLine(template.headline)
            appendLine(template.body)

            appendLine()
            appendLine("RULES:")
            appendLine("- Do not invent a number. Every figure must come from the facts above.")
            appendLine("- Do not calculate anything, including percentages or totals.")
            appendLine(
                "- Do not change the verdict. If it says they are doing badly, say that.",
            )
            appendLine("- Do not congratulate them for work the facts do not show.")
            appendLine("- Second person. No greeting, no sign-off, no emoji, no markdown.")
            appendLine("- Headline: under 60 characters. Body: 2 to 4 sentences.")
        }

        // The draft counts as supplied, because a faithful rewrite of it should never be rejected
        // for quoting a figure the draft itself already quoted.
        return NagRequest(prompt, facts + template.headline + template.body)
    }

    private fun meaning(state: VerdictState): String = when (state) {
        VerdictState.FEED_SILENT ->
            "No data is arriving at all, so this is a broken sync rather than their fault. Do " +
                "not accuse them of anything."

        VerdictState.COLD_START ->
            "There is too little history to judge them yet. Say so plainly rather than guessing."

        VerdictState.RESTING ->
            "They are inside a legitimate recovery window. Rest is earning them something here."

        VerdictState.IDLE ->
            "They have stopped for long enough that it is costing them fitness. Get them moving."

        VerdictState.STEADY ->
            "Work is going in and nothing measurably changed. Do not manufacture excitement."

        VerdictState.PROGRESSING ->
            "Something genuinely improved. Say what, and do not inflate it."

        VerdictState.RAMPING_HARD ->
            "They have ramped up faster than their base supports. This is a warning, not praise."
    }
}
