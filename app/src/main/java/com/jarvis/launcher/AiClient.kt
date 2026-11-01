package com.jarvis.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Optional "brain" for open-ended questions ("how tall is Everest?", "recommend a thriller").
 * Only used when an API key is set in local.properties (see local.properties.example).
 * Every device command works without it.
 */
class AiClient(private val apiKey: String, private val model: String) {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val executor = Executors.newSingleThreadExecutor()
    private val history = ArrayList<Pair<String, String>>() // role to text, touched only on [executor]

    /** [onReply] is called on a background thread; post to the main thread yourself. */
    fun ask(prompt: String, onReply: (String) -> Unit) {
        executor.execute { onReply(request(prompt)) }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun request(prompt: String): String {
        val messages = JSONArray()
        history.forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 220)
            .put("system", SYSTEM_PROMPT)
            .put("messages", messages)

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                return if (conn.responseCode == 401) {
                    "My API key was rejected. Please check it in local dot properties."
                } else {
                    "My connection to the network brain failed with code ${conn.responseCode}."
                }
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val text = json.getJSONArray("content").getJSONObject(0).getString("text").trim()
            history.add("user" to prompt)
            history.add("assistant" to text)
            while (history.size > 8) history.removeAt(0)
            text
        } catch (e: Exception) {
            "I couldn't reach the network just now."
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are Jarvis, a calm, witty, highly capable assistant living inside a Google TV launcher. " +
                "Your replies are spoken aloud on a TV, so answer in one to three short plain sentences. " +
                "No markdown, no lists, no emoji. If you do not know something, say so briefly."
    }
}
