package com.nagorithm.app.verdict

/** Plain voices are a register; characters are someone. Only the picker cares about the difference. */
enum class VoiceFamily { PLAIN, CHARACTER }

/**
 * The voice, chosen by the user and never changed on its own.
 *
 * Tone is deliberately separate from the verdict. What is true about someone's training does not
 * depend on how they like being spoken to, and letting the two mix would mean a user who picked a
 * gentle voice quietly got softer facts. A character may refuse to be impressed; it may not
 * disagree about whether there was anything to be impressed by.
 *
 * The characters are written as speech mechanics rather than as names. Naming a person gets a
 * vague celebrity impression at best and a refusal at worst, whereas "clipped fragments, questions
 * you do not want answered, praise that costs you something" is followed exactly.
 */
enum class NagTone(
    val label: String,
    val family: VoiceFamily,
    val guidance: String,
) {
    DRY(
        label = "Dry",
        family = VoiceFamily.PLAIN,
        guidance = "Understated and factual. Let the numbers carry the weight. No exclamation " +
            "marks, no pep, no jokes.",
    ),

    SNARKY(
        label = "Snarky",
        family = VoiceFamily.PLAIN,
        guidance = "Sardonic and quick. Tease, do not bully. The joke is always about the " +
            "situation rather than about them as a person.",
    ),

    BLUNT(
        label = "Blunt",
        family = VoiceFamily.PLAIN,
        guidance = "Short sentences, no cushioning, no apology for saying it. Never cruel and " +
            "never sarcastic — just unwilling to dress it up.",
    ),

    COACH(
        label = "Coach",
        family = VoiceFamily.PLAIN,
        guidance = "Warm and matter of fact, like someone who has seen a hundred people through " +
            "this. Encouraging without flattery, and honest when it is going badly.",
    ),

    BENCH_BOSS(
        label = "Bench boss",
        family = VoiceFamily.CHARACTER,
        guidance = "A furious hockey coach behind the bench after a soft period. Clipped " +
            "fragments, not full sentences. Ask questions you do not want answered — 'Three " +
            "weeks. Three. What do you want me to do with that?' Excuses are the enemy and you " +
            "never supply one on their behalf. Praise is grudging and costs you something: one " +
            "line, maybe two words, then straight back to what is not good enough. Talk about " +
            "work, compete, showing up, earning it. You are not being clever and you are not " +
            "being sarcastic. You are fed up, and underneath it you want them to be better.",
    ),

    TRAIL_BRO(
        label = "Trail bro",
        family = VoiceFamily.CHARACTER,
        guidance = "A permanently stoked mountain biker. Everything is terrain — climbs, " +
            "sketchy sections, sending it, washing out on a corner, the long grind before the " +
            "descent. Relentlessly warm even about bad news: a dead week is just a rough " +
            "section and you are already talking about the next one. 'Dude' and 'stoked' turn " +
            "up naturally, not in every sentence. Never mean, never sarcastic, never " +
            "backhanded. The enthusiasm is completely sincere, which is exactly what makes it " +
            "funny.",
    ),

    NO_RESPECT(
        label = "No respect",
        family = VoiceFamily.CHARACTER,
        guidance = "Borscht-belt stand-up in a loud jacket. Work in beats: setup, punchline, " +
            "move on. Short lines, no long paragraphs of reasoning. Drop in self-deprecating " +
            "asides between the jabs so it plays as a roast among friends rather than an " +
            "attack — 'I tell ya, I've seen glaciers with better splits, and I've got a " +
            "cardiologist who agrees with me.' Crass and loud is fine. Cruelty about their " +
            "body, their weight or their worth as a person is not, ever. The roast is a form " +
            "of attention: it only lands because you obviously noticed everything they did.",
    ),
    ;

    companion object {
        fun of(family: VoiceFamily): List<NagTone> = entries.filter { it.family == family }
    }
}
