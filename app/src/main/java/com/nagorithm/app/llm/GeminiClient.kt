package com.nagorithm.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What the model was asked to produce, before anything decides whether to believe it. */
data class WrittenNag(val headline: String, val body: String)

/**
 * One HTTPS POST, done by hand.
 *
 * A single JSON call to a single endpoint does not justify an HTTP stack and a serialization
 * plugin, and every dependency added here is one more thing that can break a build whose whole
 * point is running unattended on a phone.
 */
class GeminiClient(private val apiKey: String) {

    suspend fun write(prompt: String): Result<WrittenNag> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Content-Type", "application/json")
                // In a header rather than the query string, which keeps it out of request logs.
                setRequestProperty("x-goog-api-key", apiKey)
            }

            connection.outputStream.use { it.write(requestBody(prompt).toString().toByteArray()) }

            val code = connection.responseCode
            val payload = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Gemini returned $code: ${error.take(300)}")
            }

            parse(payload)
        }
    }

    /**
     * Structured output rather than asking for JSON in the prose and hoping. The schema is the
     * difference between a parse failure being impossible and being occasional.
     */
    private fun requestBody(prompt: String) = JSONObject().apply {
        put(
            "contents",
            JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))),
            ),
        )
        put(
            "generationConfig",
            JSONObject().apply {
                put("responseMimeType", "application/json")
                put(
                    "responseSchema",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject()
                                .put("headline", JSONObject().put("type", "STRING"))
                                .put("body", JSONObject().put("type", "STRING")),
                        )
                        put("required", JSONArray().put("headline").put("body"))
                    },
                )
                // Voice benefits from some looseness. The facts are not the model's to vary, and
                // the guard downstream is what enforces that rather than a low temperature.
                put("temperature", 0.9)
            },
        )
    }

    private fun parse(payload: String): WrittenNag {
        val text = JSONObject(payload)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val written = JSONObject(text)
        return WrittenNag(
            headline = written.getString("headline").trim(),
            body = written.getString("body").trim(),
        )
    }

    private companion object {
        /**
         * Flash rather than Pro. This is a rewriting job with the reasoning already done, and it
         * runs on a background worker where a slow call is a nag that arrives late.
         *
         * Pinned to a stable name rather than an alias, so a model retirement shows up as one
         * failed call with a readable reason instead of the voice quietly changing one morning.
         */
        const val MODEL = "gemini-3.6-flash"

        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        const val TIMEOUT_MILLIS = 20_000
    }
}
