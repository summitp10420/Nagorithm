package com.nagorithm.app.llm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The user's own API key, held under a hardware-backed key rather than in plain preferences.
 *
 * It is their credential and their bill, so it never leaves the device except to the provider it
 * belongs to. Storing it in the clear would put a billable secret in any backup that ever touched
 * this app.
 */
class ApiKeyStore(context: Context) {

    private val preferences by lazy {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "nagorithm-secrets",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var geminiKey: String?
        get() = preferences.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
        set(value) {
            preferences.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, value.trim())
            }.apply()
        }

    val hasKey: Boolean get() = geminiKey != null

    /**
     * Enough to show the user which key is saved without putting the whole thing back on screen
     * where it can be shoulder-read or screenshotted.
     */
    fun maskedKey(): String? = geminiKey?.let { key ->
        if (key.length <= 8) "•".repeat(key.length) else "${key.take(4)}…${key.takeLast(4)}"
    }

    private companion object {
        const val KEY_GEMINI = "gemini"
    }
}
