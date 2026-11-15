package com.example.on_device_qwen.domain.modelmanager

import com.example.on_device_qwen.model.BackendType
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.Modality
import com.example.on_device_qwen.model.ModelFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class ImportProgress(val displayLabel: String) {
    IDLE("Idle"),
    COPYING("Copying model file…"),
    VERIFYING("Verifying model header…"),
    FINALIZING("Finalizing import…"),
    READY("Import ready"),
    FAILED("Import failed"),
}

data class ModelImportState(
    val progress: ImportProgress = ImportProgress.IDLE,
    val fileName: String = "",
    val bytesCopied: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) (bytesCopied.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else null
}

data class ModelCatalogItem(
    val id: String,
    val name: String,
    val publisher: String,
    val format: ModelFormat,
    val engineType: EngineType,
    val quantization: String = "Q4_K_M",
    val fileSizeBytes: Long,
    val recommendedRamMb: Long = 4096L,
    val downloadUrl: String,
    val repoUrl: String = "",
    val isAbliterated: Boolean = false,
    val hasVision: Boolean = false,
    val description: String = "",
    val architecture: String = "Qwen2.5",
    val modalities: Set<Modality> =
        if (hasVision) setOf(Modality.TEXT, Modality.VISION) else setOf(Modality.TEXT),
    val minRamMb: Long = 2048L,
    val supportedBackends: List<BackendType> = listOf(BackendType.CPU),
    val sha256: String? = null,
    val capabilityNote: String? = null,
) {
    /** Formats byte count accurately using Android SI units matching decimal catalog sizes. */
    val fileSizeFormatted: String
        get() = when {
            fileSizeBytes <= 0L -> "0 MB"
            fileSizeBytes < 1_000_000L -> "${(fileSizeBytes / 1_000.0).roundToInt().coerceAtLeast(1)} KB"
            fileSizeBytes < 1_000_000_000L -> "${(fileSizeBytes / 1_000_000.0).roundToInt()} MB"
            else -> String.format(Locale.US, "%.2f GB", fileSizeBytes / 1_000_000_000.0)
        }

    val effectiveRepoUrl: String
        get() = repoUrl.ifEmpty {
            val resolveIdx = downloadUrl.indexOf("/resolve/")
            if (resolveIdx != -1) downloadUrl.substring(0, resolveIdx) else downloadUrl
        }

    companion object {
        val VERIFIED_CATALOG = listOf(
            ModelCatalogItem(
                id = "dolphin-3.0-qwen2.5-3b-q4km",
                name = "Dolphin 3.0 Qwen2.5 3B",
                publisher = "Cognitive Computations / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 2_050_000_000L,
                recommendedRamMb = 3400L,
                downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF/resolve/main/Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Uncensored fine-tune of Qwen 2.5 3B with strong instruction & coding performance.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 2600L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "CPU-only via bundled llama.cpp (Qwen2/Qwen2.5 architecture).",
            ),
            ModelCatalogItem(
                id = "qwen2.5-0.5b-abliterated-gguf",
                name = "Qwen 2.5 0.5B Instruct Abliterated",
                publisher = "huihui-ai / mradermacher",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 398_000_000L,
                recommendedRamMb = 1024L,
                downloadUrl = "https://huggingface.co/mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-abliterated-SFT.i1-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Ultra-lightweight uncensored assistant. Runs fast on virtually any Android smartphone.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 768L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "CPU-only via bundled llama.cpp (Qwen2/Qwen2.5 architecture).",
            ),
            ModelCatalogItem(
                id = "qwen2.5-coder-3b-abliterated-gguf",
                name = "Qwen 2.5 Coder 3B Abliterated",
                publisher = "huihui-ai / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 1_930_000_000L,
                recommendedRamMb = 3200L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-Coder-3B-Instruct-abliterated-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Uncensored 3B coding and reasoning model offering unconstrained responses.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 2500L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "CPU-only via bundled llama.cpp (Qwen2/Qwen2.5 architecture).",
            ),
            ModelCatalogItem(
                id = "qwen2.5-coder-7b-abliterated-gguf",
                name = "Qwen 2.5 Coder 7B Abliterated",
                publisher = "huihui-ai / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 4_680_000_000L,
                recommendedRamMb = 6400L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-Coder-7B-Instruct-abliterated-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Flagship 7B uncensored coding model for high-end devices with 12GB+ total RAM.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 5500L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "CPU-only via bundled llama.cpp; high memory usage on <=8GB devices.",
            ),
            ModelCatalogItem(
                id = "qwen-2.5-1.5b-instruct-gguf",
                name = "Qwen 2.5 1.5B Instruct (GGUF)",
                publisher = "Qwen / Alibaba Cloud",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 1_120_000_000L,
                recommendedRamMb = 2000L,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                repoUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",
                isAbliterated = false,
                hasVision = false,
                description = "Official quantized release of Qwen 2.5 1.5B with high speed and low memory footprint.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 1500L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "CPU-only via bundled llama.cpp (Qwen2/Qwen2.5 architecture).",
            ),
            ModelCatalogItem(
                id = "qwen-2.5-1.5b-litert",
                name = "Qwen 2.5 1.5B Instruct (LiteRT)",
                publisher = "Alibaba Cloud / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "Q8",
                fileSizeBytes = 1_597_000_000L,
                recommendedRamMb = 2400L,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                isAbliterated = false,
                hasVision = false,
                description = "Official LiteRT-LM model supporting CPU execution and hardware delegates where available.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 1800L,
                supportedBackends = listOf(BackendType.CPU, BackendType.GPU, BackendType.NPU),
                capabilityNote = "Text-only LiteRT-LM bundle; GPU/NPU delegates fall back to CPU if unsupported on device. Modalities verified at runtime.",
            ),
            ModelCatalogItem(
                id = "deepseek-r1-distill-qwen-1.5b-litert",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                publisher = "DeepSeek / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "Q8",
                fileSizeBytes = 1_833_000_000L,
                recommendedRamMb = 2600L,
                downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                isAbliterated = false,
                hasVision = false,
                description = "Reasoning model producing thinking traces via Google LiteRT-LM backend.",
                architecture = "qwen2",
                modalities = setOf(Modality.TEXT),
                minRamMb = 2000L,
                supportedBackends = listOf(BackendType.CPU, BackendType.GPU, BackendType.NPU),
                capabilityNote = "Text-only LiteRT-LM bundle; GPU/NPU delegates fall back to CPU if unsupported on device. Modalities verified at runtime.",
            ),
            ModelCatalogItem(
                id = "qwen-2-vl-2b-gguf",
                name = "Qwen2-VL 2B (GGUF Text-Only)",
                publisher = "Alibaba Cloud / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 986_000_000L,
                recommendedRamMb = 1900L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF",
                isAbliterated = false,
                hasVision = false,
                description = "Qwen2-VL 2B language weights in GGUF format (runs text-only in this build).",
                architecture = "qwen2vl",
                modalities = setOf(Modality.TEXT),
                minRamMb = 1600L,
                supportedBackends = listOf(BackendType.CPU),
                capabilityNote = "Text-only in this build: GGUF vision projector (mmproj/CLIP) is not supported in bundled llama.cpp.",
            )
        )
    }
}
