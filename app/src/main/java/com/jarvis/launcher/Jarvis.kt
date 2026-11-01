package com.jarvis.launcher

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The assistant: listens (SpeechRecognizer), understands a handful of TV commands,
 * and answers out loud (TextToSpeech).
 */
class Jarvis(private val activity: Activity, private val host: Host) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    interface Host {
        fun onStateChanged(state: State, text: String?)
        fun findApp(query: String): AppEntry?
        fun openApp(app: AppEntry)
        fun nextWallpaper()
        fun goHome()
    }

    companion object {
        const val REQ_AUDIO = 4201

        private val HOME_WORDS = setOf("home", "go home", "go to home", "take me home", "home screen")

        private val JOKES = listOf(
            "I would tell you a UDP joke, but you might not get it.",
            "Why did the TV go to therapy? It had too many unresolved channels.",
            "I tried to catch some fog earlier. I mist.",
            "Streaming is just television that asks if you're still watching."
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private val ai = AiClient(BuildConfig.ANTHROPIC_API_KEY, BuildConfig.ANTHROPIC_MODEL)
    private val voicePrefs = activity.getSharedPreferences("jarvis_voice", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var spoken: String? = null

    init {
        tts = TextToSpeech(activity.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                applySavedVoice(engine)
                engine.setPitch(Config.TTS_PITCH)
                engine.setSpeechRate(Config.TTS_RATE)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        main.post { host.onStateChanged(State.IDLE, spoken) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { host.onStateChanged(State.IDLE, spoken) }
                    }
                })
                ttsReady = true
            }
        }
    }

    // ---------------------------------------------------------------- voice

    private fun englishVoices(engine: TextToSpeech) = try {
        engine.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }
            .sortedBy { it.name }
    } catch (e: Exception) {
        emptyList()
    }

    /** Uses the voice you picked last time, else a British English one (very Jarvis). */
    private fun applySavedVoice(engine: TextToSpeech) {
        val voices = englishVoices(engine)
        val saved = voicePrefs.getString("voice", null)
        val pick = voices.firstOrNull { it.name == saved }
            ?: voices.firstOrNull { it.locale.country == "GB" }
        if (pick != null) engine.setVoice(pick)
    }

    /** Cycles through the English voices installed on the TV and remembers the choice. */
    fun nextVoice() {
        val engine = tts ?: return
        val voices = if (ttsReady) englishVoices(engine) else emptyList()
        if (voices.size < 2) {
            say("I only have one voice available on this TV.")
            return
        }
        val i = voices.indexOfFirst { it.name == engine.voice?.name }
        val next = voices[(i + 1).mod(voices.size)]
        engine.setVoice(next)
        engine.setPitch(Config.TTS_PITCH)
        engine.setSpeechRate(Config.TTS_RATE)
        voicePrefs.edit().putString("voice", next.name).apply()
        say("This is my new voice. Say change voice to hear another.")
    }

    // ---------------------------------------------------------------- listening

    fun startListening() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            say("Speech recognition isn't available on this device.")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(activity)
        r.setRecognitionListener(listener)
        recognizer = r

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)

        host.onStateChanged(State.LISTENING, null)
        r.startListening(intent)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) startListening() else say("I need microphone access to hear you.")
    }

    fun stop() {
        recognizer?.cancel()
        tts?.stop()
        host.onStateChanged(State.IDLE, null)
    }

    fun shutdown() {
        ai.shutdown()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            host.onStateChanged(State.LISTENING, null)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            host.onStateChanged(State.THINKING, null)
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "I'm having network trouble."
                else -> "Something went wrong with the microphone."
            }
            say(msg)
        }

        override fun onResults(results: Bundle?) {
            val best = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (best.isNullOrBlank()) say("I didn't catch that.") else handle(best)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { host.onStateChanged(State.LISTENING, it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---------------------------------------------------------------- understanding

    fun handle(raw: String) {
        host.onStateChanged(State.THINKING, raw)

        val text = raw.lowercase(Locale.getDefault())
            .replace(Regex("^\\s*(hey |ok |okay )?jarvis[,.!?]?\\s*"), "")
            .trim()
            .trimEnd('.', '!', '?')

        val openMatch =
            Regex("^(?:please\\s+)?(open|launch|start|run|go to|switch to|play|watch)\\s+(.+)$").find(text)
        val searchMatch = Regex("^(?:search(?:\\s+for)?|google|look up)\\s+(.+)$").find(text)

        when {
            text.isEmpty() -> say("Yes? I'm listening.")

            text in HOME_WORDS -> {
                host.goHome()
                say("Back to the home screen.")
            }

            text == "time" || Regex("\\b(what('s| is) the time|what time|current time|time is it)\\b")
                .containsMatchIn(text) -> {
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                say("It's $time.")
            }

            Regex("\\b(what('s| is) (the |today's )?date|today's date|what day|day is it)\\b").containsMatchIn(text) -> {
                val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
                say("Today is $date.")
            }

            Regex("\\b(change|switch|next|different|another)\\b.*\\bvoice\\b").containsMatchIn(text) ->
                nextVoice()

            Regex("\\b(next|change|switch)\\b.*\\b(wallpaper|background)\\b").containsMatchIn(text) -> {
                host.nextWallpaper()
                say("New wallpaper.")
            }

            Regex("\\b(volume up|louder|turn it up|raise the volume)\\b").containsMatchIn(text) ->
                adjustVolume(AudioManager.ADJUST_RAISE, "Turning it up.")

            Regex("\\b(volume down|quieter|turn it down|lower the volume)\\b").containsMatchIn(text) ->
                adjustVolume(AudioManager.ADJUST_LOWER, "Turning it down.")

            Regex("\\b(mute|unmute)\\b").containsMatchIn(text) ->
                adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, "Done.")

            Regex("\\b(who are you|your name|what are you)\\b").containsMatchIn(text) ->
                say("I'm ${Config.ASSISTANT_NAME}, the assistant built into your TV home screen.")

            text in setOf("hello", "hi", "hey", "good morning", "good evening", "good afternoon") ->
                say("Hello! How can I help?")

            Regex("\\b(thank you|thanks)\\b").containsMatchIn(text) -> say("Anytime.")

            Regex("\\bjoke\\b").containsMatchIn(text) -> say(JOKES.random())

            searchMatch != null -> webSearch(searchMatch.groupValues[1])

            openMatch != null -> {
                val verb = openMatch.groupValues[1]
                openByName(openMatch.groupValues[2], fallbackToSearch = verb == "play" || verb == "watch")
            }

            else -> {
                // A bare app name ("youtube") opens it; longer sentences are questions.
                val direct = if (text.split(" ").size <= 3) host.findApp(text) else null
                when {
                    direct != null -> launchAfterSpeaking(direct)
                    ai.isConfigured -> askAi(raw)
                    else -> say("Sorry, I don't know how to do that yet.")
                }
            }
        }
    }

    private fun askAi(question: String) {
        host.onStateChanged(State.THINKING, question)
        ai.ask(question) { reply ->
            main.post { if (!activity.isDestroyed) say(reply) }
        }
    }

    private fun openByName(query: String, fallbackToSearch: Boolean) {
        val q = query.removePrefix("the ").removeSuffix(" app").trim()
        val app = host.findApp(q)
        when {
            app != null -> launchAfterSpeaking(app)
            q.contains("setting") -> {
                say("Opening settings.")
                main.postDelayed({
                    try {
                        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        say("I couldn't open settings.")
                    }
                }, 600)
            }
            fallbackToSearch -> webSearch(q)
            else -> say("I couldn't find $q on this TV.")
        }
    }

    private fun launchAfterSpeaking(app: AppEntry) {
        say("Opening ${app.label}.")
        main.postDelayed({ host.openApp(app) }, 700)
    }

    private fun webSearch(query: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            say("Searching for $query.")
        } catch (e: Exception) {
            say("I couldn't start a search on this device.")
        }
    }

    private fun adjustVolume(direction: Int, reply: String) {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (direction == AudioManager.ADJUST_TOGGLE_MUTE) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } else {
            repeat(3) {
                val flags = if (it == 2) AudioManager.FLAG_SHOW_UI else 0
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
            }
        }
        say(reply)
    }

    // ---------------------------------------------------------------- speaking

    fun say(text: String) {
        spoken = text
        host.onStateChanged(State.SPEAKING, text)
        val engine = tts
        if (ttsReady && engine != null) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        } else {
            main.postDelayed({ host.onStateChanged(State.IDLE, text) }, 3000)
        }
    }
}
