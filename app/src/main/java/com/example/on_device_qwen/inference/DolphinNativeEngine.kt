package com.example.on_device_qwen.inference

import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Callback receives raw UTF-8 byte fragments; a model token need not be a complete character. */
fun interface ChunkListener {
    /** Return false to stop generation at the next token boundary. */
    fun onBytes(bytes: ByteArray): Boolean
}

/**
 * Local CPU-optimized llama.cpp bridge for Dolphin 3.0, Qwen3.5 Abliterated, and custom GGUF models.
 * Do not call load(), generate() or close() on the UI thread.
 *
 * Thread-safe: generation must finish or be cancelled before closing the model.
 */
class DolphinNativeEngine : Closeable {

    companion object {
        private val isNativeLoaded = try {
            System.loadLibrary("dolphin_gguf")
            true
        } catch (_: Throwable) {
            false
        }

        fun isAvailable(): Boolean = isNativeLoaded
    }

    private var modelHandle: Long = 0L
    private val isClosed = AtomicBoolean(false)

    fun load(file: File, contextSize: Int = 3072, requestedThreads: Int = 4) {
        require(file.isFile && file.canRead() && file.length() > 512 * 1024) {
            "Readable GGUF model required: ${file.absolutePath}"
        }
        // Always close any prior session safely before loading a new one
        close()

        if (!isNativeLoaded) {
            throw UnsupportedOperationException(
                "Native llama.cpp library (libdolphin_gguf.so) is not available on this ABI. " +
                "GGUF inference requires an arm64-v8a device with the native library present."
            )
        }

        val processors = Runtime.getRuntime().availableProcessors()
        val threads = requestedThreads.coerceIn(1, (processors - 1).coerceAtLeast(1))

        modelHandle = nativeOpen(file.absolutePath, contextSize, threads)
        check(modelHandle != 0L) { "Native llama.cpp engine was unable to load GGUF model: ${file.name}" }
        isClosed.set(false)
    }

    fun generate(prompt: String, maxTokens: Int = 1024, listener: ChunkListener): Int {
        check(isNativeLoaded) { "Native llama.cpp library is not available on this device ABI" }
        check(modelHandle != 0L && !isClosed.get()) { "GGUF model is not loaded — call load() before generate()" }

        return nativeGenerate(modelHandle, prompt, maxTokens, listener)
    }

    override fun close() {
        // Idempotent: safe to call even if already closed or handle is 0
        val handle = modelHandle
        modelHandle = 0L
        isClosed.set(true)
        if (handle != 0L && isNativeLoaded) {
            try {
                nativeClose(handle)
            } catch (_: Throwable) {
                // Suppress native exceptions during close to avoid crash-on-cleanup
            }
        }
    }

    private external fun nativeOpen(path: String, contextSize: Int, threads: Int): Long
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxNewTokens: Int,
        listener: ChunkListener
    ): Int
    private external fun nativeClose(handle: Long)
}
