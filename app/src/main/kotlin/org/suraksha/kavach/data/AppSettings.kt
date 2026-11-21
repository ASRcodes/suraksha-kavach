package org.suraksha.kavach.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SettingsState(
    val language: String = "en",
    val retentionDays: Int = 7,
    val protectionEnabled: Boolean = true,
    val enhanced: Boolean = false,
    val setupComplete: Boolean = false,
    val voiceEnabled: Boolean = true,
    val overlayEnabled: Boolean = false
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()

    private fun read() = SettingsState(
        language = language(prefs),
        retentionDays = prefs.getInt("retention_days", 7).takeIf { it in setOf(1, 7, 30) } ?: 7,
        protectionEnabled = prefs.getBoolean("protection", true),
        enhanced = prefs.getBoolean("enhanced", false),
        setupComplete = prefs.getBoolean("setup_complete", false),
        voiceEnabled = prefs.getBoolean("voice", true),
        overlayEnabled = prefs.getBoolean("overlay", false)
    )

    @Synchronized private fun change(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        mutable.value = read()
    }
    fun setLanguage(value: String) { require(value in setOf("en", "hi", "pa")); change { putString("language", value) } }
    fun setRetention(value: Int) { require(value in setOf(1, 7, 30)); change { putInt("retention_days", value) } }
    fun setProtection(value: Boolean) = change { putBoolean("protection", value) }
    fun setEnhanced(value: Boolean) = change { putBoolean("enhanced", value) }
    fun setVoice(value: Boolean) = change { putBoolean("voice", value) }
    fun setOverlay(value: Boolean) = change { putBoolean("overlay", value) }
    fun wasAsked(permission: String): Boolean = prefs.getBoolean("asked_" + permission, false)
    fun markAsked(permission: String) = change { putBoolean("asked_" + permission, true) }
    fun finishSetup() = change { putBoolean("setup_complete", true) }

    companion object {
        private fun language(prefs: SharedPreferences) = prefs.getString("language", null)
            ?.takeIf { it in setOf("en", "hi", "pa") }
            ?: Locale.getDefault().language.takeIf { it in setOf("en", "hi", "pa") } ?: "en"

        fun localized(context: Context): Context {
            val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language(prefs)))
            return context.createConfigurationContext(config)
        }
    }
}
