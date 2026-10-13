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
    val isAbliterated: Boolean = false,
    val hasVision: Boolean = false,
    val description: String = "",
) {
    val fileSizeFormatted: String
        get() {
            val mb = fileSizeBytes / (1024 * 1024)
            return if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"
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
                isAbliterated = true,
                hasVision = false,
                description = "High capability uncensored fine-tune of Qwen 2.5 3B with instruction & coding mastery."
            ),
            ModelCatalogItem(
                id = "huihui-qwen3.5-0.8b-abliterated",
                name = "Huihui Qwen3.5 0.8B Abliterated",
                publisher = "Huihui AI",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 580_000_000L,
                recommendedRamMb = 1200L,
                downloadUrl = "https://huggingface.co/huihui-ai/Qwen3.5-0.8B-Abliterated-GGUF/resolve/main/Qwen3.5-0.8B-Abliterated-Q4_K_M.gguf",
                isAbliterated = true,
                hasVision = false,
                description = "Ultra-fast lightweight abliterated assistant suitable for budget and mid-range devices."
            ),
            ModelCatalogItem(
                id = "huihui-qwen3.5-2b-abliterated",
                name = "Huihui Qwen3.5 2B Abliterated",
                publisher = "Huihui AI",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 1_450_000_000L,
                recommendedRamMb = 2400L,
                downloadUrl = "https://huggingface.co/huihui-ai/Qwen3.5-2B-Abliterated-GGUF/resolve/main/Qwen3.5-2B-Abliterated-Q4_K_M.gguf",
                isAbliterated = true,
                hasVision = false,
                description = "Balanced 2B abliterated model offering high reasoning throughput and unconstrained responses."
            ),
            ModelCatalogItem(
                id = "huihui-qwen3.5-4b-abliterated",
                name = "Huihui Qwen3.5 4B Abliterated",
                publisher = "Huihui AI",
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                quantization = "Q4_K_M",
                fileSizeBytes = 2_700_000_000L,
                recommendedRamMb = 4200L,
                downloadUrl = "https://huggingface.co/huihui-ai/Qwen3.5-4B-Abliterated-GGUF/resolve/main/Qwen3.5-4B-Abliterated-Q4_K_M.gguf",
                isAbliterated = true,
                hasVision = false,
                description = "High capability 4B abliterated variant for flagship smartphones with 8GB+ RAM."
            ),
            ModelCatalogItem(
                id = "qwen-2.5-1.5b-litert",
                name = "Qwen 2.5 1.5B Instruct",
                publisher = "Alibaba Cloud / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "INT4",
                fileSizeBytes = 1_050_000_000L,
                recommendedRamMb = 1800L,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct.litertlm",
                isAbliterated = false,
                hasVision = false,
                description = "Official LiteRT optimized model with hardware GPU and NPU acceleration."
            ),
            ModelCatalogItem(
                id = "qwen-2-vl-2b-litert",
                name = "Qwen2-VL 2B Multimodal",
                publisher = "Alibaba Cloud / Google LiteRT",
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                quantization = "INT4",
                fileSizeBytes = 1_850_000_000L,
                recommendedRamMb = 2800L,
                downloadUrl = "https://huggingface.co/litert-community/Qwen2-VL-2B-Instruct/resolve/main/Qwen2-VL-2B-Instruct.litertlm",
                isAbliterated = false,
                hasVision = true,
                description = "Multimodal Vision-Language model with camera & photo analysis capabilities."
            )
        )
    }
}
