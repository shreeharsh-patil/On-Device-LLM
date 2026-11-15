package com.example.on_device_qwen.inference

import android.os.Build
import android.util.Log
import com.example.on_device_qwen.model.GenerationConfig
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Callback receives valid UTF-8 byte fragments with stop-sequence holdback already applied natively. */
fun interface ChunkListener {
    /** Return false to abort generation at the next token boundary. */
    fun onBytes(bytes: ByteArray): Boolean
}

/**
 * Thread-safe CPU llama.cpp JNI bridge for GGUF models.
 * Do not call [load], [generate], [resetContext], or [close] on the main UI thread.
 */
class DolphinNativeEngine : Closeable {

    companion object {
        private const val TAG = "DolphinNativeEngine"
        private var nativeLoadError: Throwable? = null

        private val isNativeLoaded: Boolean by lazy {
            try {
                System.loadLibrary("dolphin_gguf")
                Log.i(TAG, "Successfully loaded libdolphin_gguf.so for ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                true
            } catch (t: Throwable) {
                nativeLoadError = t
                Log.e(
                    TAG,
                    "Failed to load libdolphin_gguf.so: ${t.message} (Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")})",
                    t,
                )
                false
            }
        }

        fun isAvailable(): Boolean = isNativeLoaded
        fun getLoadError(): Throwable? = nativeLoadError
    }

    private val stateLock = Any()

    @Volatile private var modelHandle: Long = 0L
    private val isClosed = AtomicBoolean(false)

    @Volatile var effectiveContextSize: Int = 0
        private set

    @Volatile var effectiveThreadCount: Int = 0
        private set

    fun load(
        file: File,
        contextSize: Int = 4096,
        requestedThreads: Int = 4,
    ) {
        require(file.isFile && file.canRead() && file.length() > 512L * 1024L) {
            "Readable GGUF model required: ${file.absolutePath}"
        }
        close()

        if (!isAvailable()) {
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val detail = nativeLoadError?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
            throw UnsupportedOperationException(
                "Native llama.cpp library (libdolphin_gguf.so) is not available on this ABI [$abis]$detail. " +
                    "GGUF inference requires an arm64-v8a device with the native library present.",
            )
        }

        val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val threads = requestedThreads.coerceIn(
            GenerationConfig.MIN_THREAD_COUNT,
            minOf(GenerationConfig.MAX_THREAD_COUNT, processors),
        )
        val clampedCtx = contextSize.coerceIn(
            GenerationConfig.MIN_CONTEXT_LENGTH,
            GenerationConfig.MAX_CONTEXT_LENGTH,
        )

        synchronized(stateLock) {
            val handle = nativeOpen(file.absolutePath, clampedCtx, threads)
            check(handle != 0L) { "Native llama.cpp engine was unable to load GGUF model: ${file.name}" }
            modelHandle = handle
            effectiveContextSize = clampedCtx
            effectiveThreadCount = threads
            isClosed.set(false)
        }
    }

    /** Backwards-compatible overload delegating to full sampler parameter entrypoint. */
    fun generate(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        repetitionPenalty: Float = 1.15f,
        listener: ChunkListener,
    ): Int {
        return generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            topK = 40,
            minP = 0.05f,
            repetitionPenalty = repetitionPenalty,
            seed = -1,
            stopSequences = emptyList(),
            listener = listener,
        )
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        minP: Float = 0.05f,
        repetitionPenalty: Float = 1.15f,
        seed: Int = -1,
        stopSequences: List<String> = emptyList(),
        listener: ChunkListener,
    ): Int {
        check(isNativeLoaded) { "Native llama.cpp library is not available on this device ABI" }
        val handle = requireActiveHandle()
        val stopsArray = if (stopSequences.isEmpty()) null else stopSequences.toTypedArray()
        return nativeGenerate(
            handle = handle,
            prompt = prompt,
            maxNewTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            topK = topK,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            seed = seed,
            stopSequences = stopsArray,
            listener = listener,
        )
    }

    /** Non-blocking native abort trigger; safe to invoke from any thread while [generate] runs. */
    fun requestAbort() {
        val handle = modelHandle
        if (handle != 0L && !isClosed.get() && isNativeLoaded) {
            try {
                nativeRequestAbort(handle)
            } catch (_: Throwable) {
            }
        }
    }

    fun resetContext(): Boolean {
        val handle = modelHandle
        if (handle == 0L || isClosed.get() || !isNativeLoaded) return false
        return try {
            nativeResetContext(handle)
        } catch (_: Throwable) {
            false
        }
    }

    fun countTokens(text: String): Int? {
        val handle = modelHandle
        if (handle == 0L || isClosed.get() || !isNativeLoaded) return null
        return try {
            nativeCountTokens(handle, text).takeIf { it >= 0 }
        } catch (_: Throwable) {
            null
        }
    }

    fun applyChatTemplate(
        messages: List<Pair<String, String>>,
        addAssistant: Boolean = true,
    ): String? {
        val handle = modelHandle
        if (handle == 0L || isClosed.get() || !isNativeLoaded || messages.isEmpty()) return null
        val roles = Array(messages.size) { messages[it].first }
        val contents = Array(messages.size) { messages[it].second }
        return try {
            nativeApplyChatTemplate(handle, roles, contents, addAssistant)
        } catch (_: Throwable) {
            null
        }
    }

    private fun requireActiveHandle(): Long {
        val handle = modelHandle
        check(handle != 0L && !isClosed.get()) { "GGUF model is not loaded — call load() before generate()" }
        return handle
    }

    override fun close() {
        val handle = synchronized(stateLock) {
            val existing = modelHandle
            modelHandle = 0L
            effectiveContextSize = 0
            effectiveThreadCount = 0
            isClosed.set(true)
            existing
        }
        if (handle != 0L && isNativeLoaded) {
            try {
                nativeRequestAbort(handle)
            } catch (_: Throwable) {
            }
            try {
                nativeClose(handle)
            } catch (_: Throwable) {
            }
        }
    }

    private external fun nativeOpen(path: String, contextSize: Int, threads: Int): Long

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repetitionPenalty: Float,
        seed: Int,
        stopSequences: Array<String>?,
        listener: ChunkListener,
    ): Int

    private external fun nativeClose(handle: Long)
    private external fun nativeRequestAbort(handle: Long)
    private external fun nativeResetContext(handle: Long): Boolean
    private external fun nativeCountTokens(handle: Long, text: String): Int
    private external fun nativeApplyChatTemplate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        addAssistant: Boolean,
    ): String?
}
