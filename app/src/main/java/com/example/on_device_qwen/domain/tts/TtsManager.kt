package com.example.on_device_qwen.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 100% offline Text-To-Speech manager with cold-start utterance queuing, sentence-aware
 * length chunking under [TextToSpeech.getMaxSpeechInputLength], and markdown sanitization.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val lock = Any()
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var isShutdown = false
    private var pendingRawText: String? = null
    private var finalUtteranceId: String? = null
    private val utteranceCounter = AtomicLong(0L)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                val isFinal = synchronized(lock) {
                    utteranceId == null || utteranceId == finalUtteranceId
                }
                if (isFinal) {
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
            }
        })
    }

    override fun onInit(status: Int) {
        val queuedToSpeak: String?
        synchronized(lock) {
            if (isShutdown) return
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isInitialized = true
                queuedToSpeak = pendingRawText
                pendingRawText = null
            } else {
                tts?.shutdown()
                tts = null
                isInitialized = false
                pendingRawText = null
                finalUtteranceId = null
                _isSpeaking.value = false
                return
            }
        }
        if (!queuedToSpeak.isNullOrBlank()) {
            speak(queuedToSpeak)
        }
    }

    fun speak(rawMarkdownText: String) {
        val cleanedText = cleanMarkdownForSpeech(rawMarkdownText)
        if (cleanedText.isBlank()) return

        val engine: TextToSpeech
        synchronized(lock) {
            if (isShutdown) return
            val existing = tts ?: return
            if (!isInitialized) {
                // Queue the latest request so cold-start taps speak once initialization finishes.
                pendingRawText = rawMarkdownText
                return
            }
            engine = existing
        }

        val maxLen = runCatching { TextToSpeech.getMaxSpeechInputLength() }
            .getOrDefault(DEFAULT_MAX_CHUNK_CHARS)
            .coerceIn(500, DEFAULT_MAX_CHUNK_CHARS)
        val chunks = splitIntoSpeechChunks(cleanedText, maxLen)
        if (chunks.isEmpty()) return

        val seqBase = utteranceCounter.incrementAndGet()
        val lastId = "tts_${seqBase}_${chunks.lastIndex}"
        synchronized(lock) {
            finalUtteranceId = lastId
        }

        var startedAny = false
        for ((index, chunk) in chunks.withIndex()) {
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "tts_${seqBase}_$index"
            val result = engine.speak(chunk, mode, null, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                startedAny = true
            }
        }
        if (startedAny) {
            _isSpeaking.value = true
        }
    }

    fun stop() {
        synchronized(lock) {
            pendingRawText = null
            finalUtteranceId = null
            tts?.stop()
        }
        _isSpeaking.value = false
    }

    fun shutdown() {
        synchronized(lock) {
            isShutdown = true
            isInitialized = false
            pendingRawText = null
            finalUtteranceId = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
        _isSpeaking.value = false
    }

    companion object {
        private const val DEFAULT_MAX_CHUNK_CHARS = 3800
        private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?(?:</think>|$)""", RegexOption.IGNORE_CASE)
        private val CODE_BLOCK_REGEX = Regex("""```[\s\S]*?```""")
        private val MATH_BLOCK_REGEX = Regex("""\$\$[\s\S]*?\$\$""")
        private val TABLE_DIVIDER_REGEX = Regex("""^\s*\|?(?:\s*:?-{2,}:?\s*\|)+\s*:?-{0,}:?\s*$""", RegexOption.MULTILINE)

        internal fun cleanMarkdownForSpeech(text: String): String {
            return text
                .replace(THINK_BLOCK_REGEX, "")
                .replace(CODE_BLOCK_REGEX, " Code block omitted. ")
                .replace(MATH_BLOCK_REGEX, " Math formula omitted. ")
                .replace(Regex("""\$([^$\n]+)\$"""), "$1")
                .replace(TABLE_DIVIDER_REGEX, "")
                .replace(Regex("""^\s*>\s*""", RegexOption.MULTILINE), "")
                .replace("|", ", ")
                .replace(Regex("""`([^`]+)`"""), "$1")
                .replace(Regex("""!\[([^\]]*)\]\([^\)]+\)"""), "$1")
                .replace(Regex("""\[([^\]]+)\]\([^\)]+\)"""), "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
                .replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
                .replace(Regex("""^[-*+]\s+""", RegexOption.MULTILINE), "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .replace(Regex("""[ \t]{2,}"""), " ")
                .trim()
        }

        internal fun splitIntoSpeechChunks(text: String, maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS): List<String> {
            val clean = text.trim()
            if (clean.isEmpty()) return emptyList()
            if (clean.length <= maxChunkChars) return listOf(clean)

            val chunks = mutableListOf<String>()
            var start = 0
            while (start < clean.length) {
                val endLimit = (start + maxChunkChars).coerceAtMost(clean.length)
                if (endLimit == clean.length) {
                    chunks.add(clean.substring(start).trim())
                    break
                }
                var cut = -1
                val minSearch = start + (maxChunkChars / 2)
                for (sep in charArrayOf('.', '!', '?', '\n', ',', ' ')) {
                    val idx = clean.lastIndexOf(sep, endLimit - 1)
                    if (idx >= minSearch) {
                        cut = idx + 1
                        break
                    }
                }
                if (cut <= start) cut = endLimit
                val piece = clean.substring(start, cut).trim()
                if (piece.isNotEmpty()) chunks.add(piece)
                start = cut
                while (start < clean.length && clean[start].isWhitespace()) start++
            }
            return chunks
        }
    }
}
