# On-Device LLM & Qwen 2.0: Offline AI Assistant 🚀

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![LiteRT-LM](https://img.shields.io/badge/LiteRT--LM-0.17.1-blue.svg)](https://github.com/google-ai-edge/litert-lm)
[![llama.cpp](https://img.shields.io/badge/llama.cpp-GGUF-orange.svg)](https://github.com/ggml-org/llama.cpp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/shreeharsh-patil/On-Device-LLM?color=blue&logo=github)](https://github.com/shreeharsh-patil/On-Device-LLM/releases)

**On-Device LLM** is a privacy-first, 100% offline AI assistant for Android powered by a unified multi-engine architecture (**LiteRT-LM + llama.cpp**). It runs **Dolphin 3.0 Qwen2.5 3B**, **Qwen 2.5**, **Qwen 3**, **Qwen2-VL** multimodal vision models, and less-restricted **Huihui Qwen3.5 Abliterated** models directly on your phone's processor.

No cloud APIs. No internet connection. Zero data leaving your phone.

---

## ✨ Features

- 🧠 **100% Offline & Private Inference**: Everything runs on-device. Your conversations, system prompts, and photos never leave your device.
- 🐬 **Dolphin 3.0 & Less-Restricted GGUF Models**: Native support for **Dolphin 3.0 Qwen2.5 3B** (`Q4_K_M`) and **Huihui Qwen3.5 Abliterated** (`0.8B`, `2B`, `4B`) through an optimized llama.cpp C++/JNI backend.
- 👁️ **Multimodal Vision-Language Support (VLM)**: Native image understanding with **Qwen2-VL-2B**. Attach photos directly from your gallery to inspect images, perform OCR, and ask visual questions.
- 💬 **ChatGPT-Inspired User Experience**:
  - Edge-to-edge layout with adaptive dark and light themes following system preferences.
  - Floating rounded message composer with auto-expansion, photo preview, and an instant **Stop Generation** button.
  - Navigation drawer with date-grouped chat history ("Today", "Yesterday", "Previous 7 Days", "Older"), title editing, and search.
  - Rich Markdown rendering with dark syntax-highlighted code blocks, horizontal scroll, and one-tap **Copy Code** action.
  - Collapsible reasoning view for models generating `<think>...</think>` tags with pulsing animation.
  - Message action toolbar: Copy response, Share message, and Regenerate response.
- 🗄️ **Persistent SQLite History**: Full multi-session persistent chat storage. Saved conversations persist across model switches, app backgrounding, and device reboots.
- 🌡️ **Adaptive Thermal & Battery Management**:
  - Monitors real Android OS thermal status callbacks (`PowerManager.OnThermalStatusChangedListener`), thermal headroom, and battery telemetry.
  - **Eco Mode**: Conservative CPU threads (capping at 2) for maximum battery life and minimum heating.
  - **Balanced Mode (Default)**: Dynamic resource allocation balancing throughput and device temperature.
  - **Performance Mode**: Unlocks sustained high-speed inference while respecting system thermal safety.
- 📊 **Real-Time Telemetry Badge**: Live tokens-per-second (TPS), time-to-first-token (TTFT in ms), active execution backend, token count, and elapsed time.
- 📂 **Advanced Model Management**:
  - Import `.gguf` and `.litertlm` models directly from device storage via Android Storage Access Framework (SAF).
  - Built-in Model Catalog featuring verified Hugging Face download links, quantization details, and recommended RAM requirements.
  - RAM safety monitor to prevent out-of-memory crashes before loading oversized models.

---

## 📦 Verified Model Catalog

| Model | Format | Quantization | Size | Recommended RAM | Source |
|---|---|---|---|---|---|
| **Dolphin 3.0 Qwen2.5 3B** | GGUF | Q4_K_M | ~2.0 GB | 3.2 GB | [HuggingFace: bartowski/Dolphin3.0-Qwen2.5-3b-GGUF](https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF) |
| **Qwen 2.5 0.5B Instruct Abliterated** | GGUF | Q4_K_M | ~398 MB | 0.9 GB | [HuggingFace: mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF](https://huggingface.co/mradermacher/Qwen2.5-0.5B-Instruct-abliterated-SFT-i1-GGUF) |
| **Qwen 2.5 Coder 3B Abliterated** | GGUF | Q4_K_M | ~1.9 GB | 2.8 GB | [HuggingFace: bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF](https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-abliterated-GGUF) |
| **Qwen 2.5 Coder 7B Abliterated** | GGUF | Q4_K_M | ~4.7 GB | 5.5 GB | [HuggingFace: bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF](https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-abliterated-GGUF) |
| **Qwen 2.5 1.5B Instruct (GGUF)** | GGUF | Q4_K_M | ~1.1 GB | 1.8 GB | [HuggingFace: Qwen/Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |
| **Qwen 2.5 1.5B Instruct (LiteRT)** | LiteRT | Q8 | ~1.6 GB | 2.2 GB | [HuggingFace: litert-community/Qwen2.5-1.5B-Instruct](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct) |
| **DeepSeek-R1 Distill Qwen 1.5B** | LiteRT | Q8 | ~1.8 GB | 2.5 GB | [HuggingFace: litert-community/DeepSeek-R1-Distill-Qwen-1.5B](https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B) |
| **Qwen2-VL 2B Multimodal** | GGUF | Q4_K_M | ~986 MB | 1.8 GB | [HuggingFace: bartowski/Qwen2-VL-2B-Instruct-GGUF](https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF) |

---

## 📲 How to Push Models to Device

Connect your Android phone via USB and run:

```bash
# Push Dolphin 3.0 GGUF
adb push Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf /sdcard/Download/

# Push Qwen2-VL Multimodal Vision model
adb push Qwen2-VL-2B-Instruct.litertlm /sdcard/Download/

# Push Qwen 2.5 LiteRT model
adb push Qwen2.5-1.5B-Instruct.litertlm /sdcard/Download/
```

Or tap **Model Management > Import Model** in the app to select any `.gguf` or `.litertlm` file directly from device storage.

---

## 🛠️ Building & Installation

### ⬇️ Direct Download (Ready to Install)
Grab the latest pre-compiled, signed APK from [GitHub Releases](https://github.com/shreeharsh-patil/On-Device-LLM/releases/latest) and install it directly on your Android device (Android 8.0+ / API 26+).

### Prerequisites (For Developers)
- Android Studio Ladybug / Meerkat or later
- JDK 17 / 21
- Android SDK 35+ (compileSdk 37, targetSdk 36, minSdk 26)
- Android NDK 27+ and CMake 3.22.1+

### Build Debug APK via Command Line
```bash
# Clone the repository
git clone https://github.com/shreeharsh-patil/On-Device-LLM.git
cd On-Device-LLM

# Run Unit Tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏛️ Architecture Overview

```text
com.example.on_device_qwen/
├── data/
│   ├── db/
│   │   └── ChatDatabaseHelper.kt        // Persistent SQLite store
│   ├── preferences/
│   │   └── AppPreferences.kt            // Settings persistence
│   └── repository/
│       └── ConversationRepository.kt    // Data access layer
├── domain/
│   ├── engine/
│   │   ├── InferenceEngine.kt           // Unified inference contract
│   │   ├── LiteRTInferenceEngine.kt     // LiteRT-LM backend (Qwen 2.5, Qwen2-VL)
│   │   ├── LlamaCppInferenceEngine.kt   // llama.cpp GGUF backend (Dolphin, Abliterated)
│   │   ├── InferenceEngineFactory.kt    // Dynamic backend router
│   │   └── GgufMetadataParser.kt        // GGUF header parser & RAM estimator
│   ├── modelmanager/
│   │   ├── ModelCatalogItem.kt          // Verified model catalog
│   │   └── ModelRepository.kt           // Discovery & SAF file import
│   └── performance/
│       ├── ThermalPerformanceManager.kt // PowerManager thermal callbacks & battery telemetry
│       └── MemoryMonitor.kt             // ActivityManager RAM inspection
├── inference/
│   └── DolphinNativeEngine.kt           // JNI bridge to libdolphin_gguf.so
├── ui/
│   ├── components/
│   │   ├── ChatComposer.kt              // Floating message composer with Stop action
│   │   ├── NavigationDrawer.kt          // ChatGPT-style sidebar with date grouping
│   │   ├── MarkdownText.kt              // Markdown renderer with syntax-highlighted code
│   │   ├── ThinkingSection.kt           // Reasoning drawer (<think>...</think>)
│   │   ├── BenchmarkBadge.kt            // Telemetry badge (TPS, TTFT, Backend)
│   │   ├── ModelManagerDialog.kt        // Multi-tab model manager & catalog
│   │   └── SettingsDialog.kt            // Grouped configuration dialog
│   ├── theme/
│   │   └── Theme.kt                     // Dynamic system dark/light theming
│   └── ChatScreen.kt                    // Main host screen
└── MainActivity.kt
```

---

## 📄 License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
