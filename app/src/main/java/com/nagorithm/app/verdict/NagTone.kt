package com.nagorithm.app.verdict

/**
 * The voice, chosen by the user and never changed on its own.
 *
 * Tone is deliberately separate from the verdict. What is true about someone's training does not
 * depend on how they like being spoken to, and letting the two mix would mean a user who picked a
 * gentle voice quietly got softer facts.
 */
enum class NagTone(val label: String, val guidance: String) {
    DRY(
        label = "Dry",
        guidance = "Understated and factual. Let the numbers carry the weight. No exclamation " +
            "marks, no pep, no jokes.",
    ),

    SNARKY(
        label = "Snarky",
        guidance = "Sardonic and quick. Tease, do not bully. The joke is always about the " +
            "situation rather than about them as a person.",
    ),

    BLUNT(
        label = "Blunt",
        guidance = "Short sentences, no cushioning, no apology for saying it. Never cruel and " +
            "never sarcastic — just unwilling to dress it up.",
    ),

    COACH(
        label = "Coach",
        guidance = "Warm and matter of fact, like someone who has seen a hundred people through " +
            "this. Encouraging without flattery, and honest when it is going badly.",
    ),
}
