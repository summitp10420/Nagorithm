package com.nagorithm.app.llm

import com.nagorithm.app.verdict.Nag
import com.nagorithm.app.verdict.NagSource
import com.nagorithm.app.verdict.NagTemplates
import com.nagorithm.app.verdict.NagTone
import com.nagorithm.app.verdict.Verdict

/** The nag, plus what happened on the way to producing it. */
data class WritingOutcome(val nag: Nag, val note: String)

/**
 * Chooses the words, having been given no say in the meaning.
 *
 * Every path out of here returns a usable nag. Without a key, with no signal, on a rejected
 * response — the template goes out, and the app is fully functional having never made a network
 * call. The model is an upgrade to the voice, never a dependency of the product.
 */
class NagWriter(private val client: GeminiClient?) {

    suspend fun write(verdict: Verdict, tone: NagTone): WritingOutcome {
        val template = NagTemplates.render(verdict)
        val model = client ?: return WritingOutcome(template, "No API key, so this is a template.")

        val request = NagPrompt.build(verdict, template, tone)

        val written = model.write(request.prompt).getOrElse { failure ->
            return WritingOutcome(
                template,
                "Model call failed (${failure.message?.take(200)}), so this is a template.",
            )
        }

        val checked = NagGuard.check(
            "${written.headline} ${written.body}",
            request.supplied,
        )

        return if (checked.accepted) {
            WritingOutcome(
                Nag(written.headline, written.body, NagSource.MODEL),
                "Written by the model in a ${tone.label.lowercase()} voice. ${checked.reason}",
            )
        } else {
            // Silently swapping in the template would hide the model misbehaving, and the whole
            // reason the guard exists is that a fabricated figure must never be indistinguishable
            // from a real one.
            WritingOutcome(template, "Model answer rejected: ${checked.reason}")
        }
    }
}
