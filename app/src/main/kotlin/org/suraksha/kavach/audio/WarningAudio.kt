package org.suraksha.kavach.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import org.suraksha.kavach.R
import java.util.Locale

/** Static warning text only. Never speaks or submits the original message to a TTS engine. */
class WarningAudio(private val context: Context) {
    val playing = MutableStateFlow(false)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var generation = 0

    fun play(language: String) {
        stop()
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0 ||
            notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        val attributes = attributes()
        try {
            player = MediaPlayer.create(context, resource(language), attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
            val current = player
            if (current == null) { fallback(language, generation); return }
            current.setOnCompletionListener { if (player === it) { player = null; playing.value = false }; it.release() }
            current.setOnErrorListener { failed, _, _ ->
                if (player === failed) { player = null; playing.value = false; failed.release(); fallback(language, generation) }
                true
            }
            current.start()
            playing.value = true
        } catch (_: Exception) { player?.release(); player = null; fallback(language, generation) }
    }

    private fun fallback(language: String, token: Int) {
        tts = TextToSpeech(context) { status ->
            if (token != generation || status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            runCatching {
                val voice = engine.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == language }
                    ?.maxByOrNull { it.quality } ?: return@runCatching
                engine.voice = voice
                engine.setAudioAttributes(attributes())
                engine.setSpeechRate(0.88f)
                val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
                val localized = context.createConfigurationContext(config)
                engine.speak(localized.getString(R.string.spoken_warning), TextToSpeech.QUEUE_FLUSH, Bundle(), "kavach-warning")
            }
        }
    }

    fun stop() {
        generation++
        player?.runCatching { stop(); release() }
        player = null
        tts?.runCatching { stop(); shutdown() }
        tts = null
        playing.value = false
    }

    companion object {
        fun resource(language: String): Int = when (language) { "hi" -> R.raw.warning_hi; "pa" -> R.raw.warning_pa; else -> R.raw.warning_en }
        fun uri(context: Context, language: String): Uri = ("android.resource://" + context.packageName + "/raw/warning_" + language.takeIf { it in setOf("en", "hi", "pa") }.orEmpty().ifEmpty { "en" }).toUri()
        fun attributes(): AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
}
