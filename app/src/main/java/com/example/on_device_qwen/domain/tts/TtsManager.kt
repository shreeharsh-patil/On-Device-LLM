package com.example.on_device_qwen.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 100% Offline Text-To-Speech engine for reading assistant responses aloud.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isInitialized = true
        } else {
            // BUG-009 FIX: Shutdown the broken TTS engine instead of leaving a zombie object alive
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }

    fun speak(rawMarkdownText: String) {
        if (!isInitialized || tts == null) return

        // Clean markdown artifacts for natural listening
        val cleanedText = cleanMarkdownForSpeech(rawMarkdownText)
        if (cleanedText.isBlank()) return

        val utteranceId = "msg_${System.currentTimeMillis()}"
        // BUG-010 FIX: Only set _isSpeaking=true if speak() succeeds; avoids stuck
        // "speaking" state when the TTS engine returns ERROR silently.
        val result = tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            _isSpeaking.value = true
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun cleanMarkdownForSpeech(text: String): String {
        return text
            // Remove code blocks
            .replace(Regex("""```[\s\S]*?```"""), " Code block omitted. ")
            // Remove inline code
            .replace(Regex("""`([^`]+)`"""), "$1")
            // Remove markdown links [text](url) -> text
            .replace(Regex("""\[([^\]]+)\]\([^\)]+\)"""), "$1")
            // Remove bold/italics
            .replace("**", "")
            .replace("*", "")
            .replace("### ", "")
            .replace("## ", "")
            .replace("# ", "")
            .replace(Regex("""^[-*]\s+""", RegexOption.MULTILINE), "")
            .trim()
    }
}
