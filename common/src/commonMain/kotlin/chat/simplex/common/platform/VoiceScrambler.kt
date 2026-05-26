package chat.simplex.common.platform

/**
 * Voice scrambler effect modes.
 * 6 truly distinct disguises — receiver cannot identify the speaker.
 */
enum class VoiceEffect(val label: String) {
    NORMAL("Normal"),
    VILLAIN("Villain"),         // -7st deep menacing (Thanos)
    HELIUM("Helium"),           // +7st high child/chipmunk
    ROBOT("Robot"),              // 0st metallic tremolo
    DEMON("Demon"),              // -10st scary deep
    GHOST("Ghost"),              // -3st eerie echo
    CYBORG("Cyborg"),            // +4st digital bit-crush
    PARANOID("Paranoid"),        // Irreversible — destroys pitch, formants & prosody
    RANDOM("Random");            // picks one of 6 disguises at random

    companion object {
        // RANDOM only picks from the 6 artistic disguises (not PARANOID — that is an explicit choice)
        val scrambleEffects = listOf(VILLAIN, HELIUM, ROBOT, DEMON, GHOST, CYBORG)

        fun randomScramble(): VoiceEffect = scrambleEffects.random()
    }

    val isScrambled: Boolean get() = this != NORMAL && this != RANDOM
}

/**
 * Platform-specific voice scrambler.
 * Applies the selected effect to a recorded audio file.
 *
 * Returns the path to the processed file, or the original path if NORMAL or on error.
 */
expect object VoiceScramblerProcessor {
    fun process(filePath: String, effect: VoiceEffect): String
}
