package com.nagorithm.app.llm

import com.nagorithm.app.verdict.Nag
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.Verdict
import com.nagorithm.app.verdict.VerdictState

/**
 * A prompt, and the only strings whose digits the answer is allowed to contain.
 *
 * Those are the user's own words — a goal like "sub-3 marathon" has to survive being quoted back.
 * Everything the app computed is deliberately absent from this list, which is what makes reciting
 * a load figure impossible rather than merely discouraged.
 */
data class NagRequest(val prompt: String, val quotable: List<String>)

/**
 * Builds the only thing the model ever sees.
 *
 * The verdict is settled before this point and handed over as a fixed conclusion, so the model
 * chooses which of the last few weeks is worth mentioning but never what any of it means. Giving it
 * the sessions as well as the conclusion is what buys an observation — "the swim came after the row
 * and the lift" — rather than a restatement, and observations are the only reason anyone reads a
 * second one of these.
 *
 * It is given the figures and forbidden from printing them. That is deliberate rather than
 * inconsistent: it needs to see that a swim was seven seconds per hundred quicker in order to know
 * the week went well, and the user needs to be told they are getting fitter, not shown the
 * subtraction. Numbers in, words out.
 */
object NagPrompt {

    private const val ROLE =
        "You are the voice of a training app. It has already decided what is true. You decide " +
            "what is worth saying about it, and how to say it."

    /**
     * The product, in one paragraph.
     *
     * Not an implementation detail that happens to be prose. Everything upstream — the load
     * scoring, the ratios, the schedule — is infrastructure nobody ever sees; this governs the
     * thing they actually receive, and is therefore the reason they will or will not open the app
     * again. It is a constant rather than a line buried in the builder below so that it can be
     * read and argued with as the specification it is.
     *
     * The last job earns its place. An earlier draft listed four and ended on the same instruction
     * to cut anything that did none of them, which quietly told the model to delete the whole
     * message on a rest day, during a broken sync, and before there was enough history to judge
     * anything — three of the seven verdicts it has to speak for. Telling someone where they stand
     * is not a lesser purpose than pushing them out of the door; on those days it is the only
     * honest one available.
     */
    private const val PURPOSE =
        "WHY THIS MESSAGE EXISTS: to keep one person training, and to tell them honestly where " +
            "they stand. Nothing else. Every line should do one of these jobs — get them moving " +
            "when they have stopped, acknowledge real progress when it is there, name stagnation " +
            "without dressing it up, warn them off doing too much too quickly, or give them a " +
            "straight answer about where they are, including when that answer is that resting is " +
            "right today, that there is not enough history to say yet, or that the app has " +
            "stopped receiving their data. If a sentence does none of those, cut it."

    fun build(
        verdict: Verdict,
        template: Nag,
        tone: NagTone,
        picture: List<String> = emptyList(),
        notes: List<String> = emptyList(),
        /** The raw text of the notes, without the ages [notes] wraps them in. */
        noteTexts: List<String> = emptyList(),
    ): NagRequest {
        val prompt = buildString {
            appendLine(ROLE)
            appendLine()
            appendLine(PURPOSE)
            appendLine()
            appendLine("VOICE: ${tone.guidance}")
            appendLine()
            appendLine("THE VERDICT, WHICH IS SETTLED: ${verdict.state.name} — ${meaning(verdict.state)}")
            appendLine()

            verdict.goal?.let {
                appendLine("THEIR GOAL, IN THEIR WORDS: \"${it.text}\"")
                appendLine(
                    "Tie the message back to this. It is the only thing that decides which of " +
                        "the sessions below actually mattered.",
                )
                appendLine()
            }

            if (picture.isNotEmpty()) {
                picture.forEach(::appendLine)
                appendLine()
            }

            if (notes.isNotEmpty()) {
                notes.forEach(::appendLine)
                appendLine()
                appendLine(NOTES_GUIDANCE)
                appendLine()
            }

            appendLine("WHAT THE APP WORKED OUT. THIS IS FOR YOUR UNDERSTANDING, NOT FOR QUOTING:")
            verdict.reasons.forEach { appendLine("- $it") }

            appendLine()
            appendLine("A PLAIN VERSION, FOR CONTENT NOT STYLE:")
            appendLine(template.headline)
            appendLine(template.body)

            appendLine()
            appendLine("RULES:")
            RULES.forEach { appendLine("- $it") }
        }

        // Only the user's own words. Everything the app computed is left out on purpose, so a
        // recited load figure has nothing to match against and fails the guard. The note *texts*
        // rather than the formatted note lines, because the lines carry ages ("11 days ago") that
        // the app computed, and those would otherwise make their digits quotable too.
        val quotable = listOfNotNull(verdict.goal?.text) + noteTexts

        return NagRequest(prompt, quotable)
    }

    /**
     * How to read what the user said about themselves.
     *
     * Kept as its own block because it is the only part of the prompt asking for a judgement rather
     * than a style: whether the subjective account and the measurements agree, and what it means
     * when they do not. The closing line is not decoration either — a character voice told to be
     * funny will otherwise use "I feel stronger" as a straight line, which is the app dismissing
     * the one piece of evidence it did not have to infer.
     */
    private const val NOTES_GUIDANCE =
        "How they feel is real evidence, not decoration. Perceived strength, sleep and energy " +
            "track genuine changes in fitness and often move before the splits do. Weigh it " +
            "against the sessions and say which way the two point. When they agree, say so " +
            "plainly and affirm it — someone feeling stronger while their times improve has the " +
            "most reliable signal there is, and most people get only one of the two. When they " +
            "disagree, that is worth saying as well: feeling good after a fortnight of nothing " +
            "is the cue to start, and feeling flat while the times keep dropping is usually " +
            "accumulated fatigue rather than lost fitness. Never mock how they say they feel, " +
            "even in a character voice."

    /**
     * The house style, in priority order. The first two are the ones that changed the app: they
     * are why a message can no longer be wrong about a number, having none to be wrong about.
     */
    private val RULES = listOf(
        "Write no numbers. No digits at all, and no figures spelled out either: no paces, " +
            "splits, distances, heart rates, loads, ratios, percentages or totals. The user has " +
            "a watch app full of those and does not need another.",

        "Say the same thing in plain comparisons instead. \"Quicker than three weeks ago\", " +
            "\"your best yet at that distance\", \"far more than the weeks behind it were " +
            "carrying\". This is not a softer claim than the figure, it is the same claim in " +
            "language somebody would actually use.",

        "Most of what you have been given should go unmentioned. It is there so you can pick the " +
            "two or three things worth noticing, not so you can list it.",

        "Prefer an observation to a summary. That a hard swim came after a row and a lift says " +
            "more than any measurement of it could.",

        "No stat blocks, no bullet lists, no headings.",

        "Do not change the verdict. The voice changes how it lands, never what is true. Stay in " +
            "character while delivering bad news rather than softening it.",

        "Do not congratulate them for work the sessions do not show.",

        "Length follows substance. A week where nothing happened is a line or two. A session " +
            "worth talking about earns a full paragraph.",

        "Second person. No greeting, no sign-off, no emoji, no markdown.",
    )

    private fun meaning(state: VerdictState): String = when (state) {
        VerdictState.FEED_SILENT ->
            "No data is arriving at all, so this is a broken sync rather than their fault. Do " +
                "not accuse them of anything."

        VerdictState.COLD_START ->
            "There is too little history to judge them yet. Say so plainly rather than guessing, " +
                "and do not treat it as their failing — it is the app's."

        VerdictState.RESTING ->
            "They are inside a legitimate recovery window. Rest is earning them something here."

        VerdictState.IDLE ->
            "They have stopped for long enough that it is costing them fitness. Get them moving."

        VerdictState.STEADY ->
            "Work is going in and nothing measurably changed. Do not manufacture excitement, and " +
                "do not treat a consistent week as a failure either."

        VerdictState.PROGRESSING ->
            "Something genuinely improved. Say what, and do not inflate it."

        VerdictState.RAMPING_HARD ->
            "They have ramped up faster than their base supports. This is a warning, not praise. " +
                "Do not scold them for training — the problem is the jump, not the effort."
    }
}
