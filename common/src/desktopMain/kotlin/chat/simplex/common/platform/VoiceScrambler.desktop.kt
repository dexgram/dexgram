package chat.simplex.common.platform

actual object VoiceScramblerProcessor {
    actual fun process(filePath: String, effect: VoiceEffect): String {
        // Desktop voice scrambling not yet implemented
        return filePath
    }
}
