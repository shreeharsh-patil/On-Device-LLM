package com.example.on_device_qwen.domain.modelmanager

import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelFormat

data class ModelCatalogItem(
    val id: String,
    val name: String,
    val publisher: String,
    val format: ModelFormat,
    val engineType: EngineType,
    val quantization: String,
    val fileSizeBytes: Long,
    val recommendedRamMb: Long,
    val downloadUrl: String,
    val repoUrl: String = "",
    val isAbliterated: Boolean = false,
    val hasVision: Boolean = false,
    val description: String = "",
) {
    val fileSizeFormatted: String
        get() {
            val mb = fileSizeBytes / (1024 * 1024)
            return if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"
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
                recommendedRamMb = 3200L,
                downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF/resolve/main/Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "High capability uncensored fine-tune of Qwen 2.5 3B with instruction & coding mastery."
            ),
            ModelCatalogItem(
                id = "qwen2.5-0.5b-abliterated-gguf",
                name = "Qwen 2.5 0.5B Instruct Abliterated",
                publisher = "huihui-ai / mradermacher",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 398_000_000L,
                recommendedRamMb = 900L,
                downloadUrl = "https://huggingface.co/mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-abliterated-SFT.i1-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Ultra-lightweight uncensored assistant. Runs fast on virtually any Android smartphone."
            ),
            ModelCatalogItem(
                id = "qwen2.5-coder-3b-abliterated-gguf",
                name = "Qwen 2.5 Coder 3B Abliterated",
                publisher = "huihui-ai / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 1_930_000_000L,
                recommendedRamMb = 2800L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-Coder-3B-Instruct-abliterated-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Uncensored 3B coding and reasoning model offering unconstrained responses."
            ),
            ModelCatalogItem(
                id = "qwen2.5-coder-7b-abliterated-gguf",
                name = "Qwen 2.5 Coder 7B Abliterated",
                publisher = "huihui-ai / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 4_680_000_000L,
                recommendedRamMb = 5500L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-Coder-7B-Instruct-abliterated-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF",
                isAbliterated = true,
                hasVision = false,
                description = "Flagship 7B uncensored coding model for high-end devices with 12GB+ RAM."
            ),
            ModelCatalogItem(
                id = "qwen-2.5-1.5b-instruct-gguf",
                name = "Qwen 2.5 1.5B Instruct (GGUF)",
                publisher = "Qwen / Alibaba Cloud",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 1_120_000_000L,
                recommendedRamMb = 1800L,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                repoUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",
                isAbliterated = false,
                hasVision = false,
                description = "Official quantized release of Qwen 2.5 1.5B with high speed and low memory footprint."
            ),
            ModelCatalogItem(
                id = "qwen-2.5-1.5b-litert",
                name = "Qwen 2.5 1.5B Instruct (LiteRT)",
                publisher = "Alibaba Cloud / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "Q8",
                fileSizeBytes = 1_597_000_000L,
                recommendedRamMb = 2200L,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                isAbliterated = false,
                hasVision = false,
                description = "Official LiteRT-LM optimized model with hardware GPU and NPU acceleration."
            ),
            ModelCatalogItem(
                id = "deepseek-r1-distill-qwen-1.5b-litert",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                publisher = "DeepSeek / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "Q8",
                fileSizeBytes = 1_833_000_000L,
                recommendedRamMb = 2500L,
                downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                repoUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                isAbliterated = false,
                hasVision = false,
                description = "Reasoning model producing thinking traces via Google LiteRT-LM backend."
            ),
            ModelCatalogItem(
                id = "qwen-2-vl-2b-gguf",
                name = "Qwen2-VL 2B Multimodal",
                publisher = "Alibaba Cloud / bartowski",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 986_000_000L,
                recommendedRamMb = 1800L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
                repoUrl = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF",
                isAbliterated = false,
                hasVision = true,
                description = "Multimodal Vision-Language model for camera OCR, document Q&A, and photo analysis."
            )
        )
    }
}
