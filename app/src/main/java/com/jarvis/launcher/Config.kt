package com.jarvis.launcher

/** Quick personalisation. Change these and rebuild. */
object Config {
    /** Shown in the greeting, e.g. "Good evening, Tony". Leave empty for just "Good evening". */
    const val OWNER_NAME = ""

    /** The assistant's name as Jarvis introduces himself. */
    const val ASSISTANT_NAME = "Jarvis"

    /** Voice character. Lower pitch + slightly slower = calmer, butler-like delivery. */
    const val TTS_PITCH = 0.85f
    const val TTS_RATE = 0.95f
}
