<p align="center">
  <img src="docs/logo.png" width="160" height="160" alt="On-Device LLM Logo" />
</p>

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

## 🚀 What's New in v1.4.0 (Sovereign Release)

- 👑 **Sovereign Design System UI/UX Overhaul**: Complete interface refresh built on high-contrast cyberpunk and dark-mode tokens, adaptive elevation surfaces, ergonomic typography, fluid animations, and responsive layouts across phones, foldables, and tablets.
- 📐 **100% Offline-First Canvas Mermaid Rendering**: Native flowchart, sequence diagram, class diagram, and state diagram rendering directly on the Android Canvas with zero WebViews, zero external JavaScript engines, and zero internet permissions.
- ⚡ **LiteRT-LM & llama.cpp Dual-Engine Hardening**: Hardened native C++/JNI bindings (`libdolphin_gguf.so`) with thread-safe inference, big.LITTLE core affinity, zero native memory leaks, and unified prefill/decode telemetry.
- 🗄️ **SQLite v3 Migration & Long-Term Memory**: Database upgrade to schema v3 with Write-Ahead Logging (WAL), foreign key cascade constraints, indexed conversation search, and autonomous cross-session long-term memory consolidation with semantic fact extraction.
- 🌡️ **Adaptive Thermal & Memory Governors**: Real-time integration with `PowerManager.OnThermalStatusChangedListener` and `MemoryMonitor` to dynamically throttle CPU threads and calculate pre-flight RAM headroom, preventing thermal throttling freezes and OOM crashes.
- 🛡️ **Zero Network Attack Surface**: Completely excluded `android.permission.INTERNET` from both debug and release builds, with cloud-backup exclusions protecting all chat databases, preferences, and model weights.

---

## ✨ Features

- 🧠 **100% Offline & Private Inference**: Zero cloud telemetry, zero remote dependencies, and zero internet permissions (`android.permission.INTERNET` strictly absent). All tokens, system prompts, memories, and photos process entirely on-device.
- 👑 **Sovereign Design System UI/UX**:
  - Immersive edge-to-edge layout with adaptive dark and light themes using high-contrast cyberpunk and deep-space tokens.
  - Floating rounded message composer (`ChatComposer`) with auto-expanding input, image attachment chips with previews, and instantaneous generation abort.
  - Sovereign navigation drawer with date-grouped history (*Today*, *Yesterday*, *Previous 7 Days*, *Older*), real-time search, inline renaming, and conversation pinning.
  - Collapsible reasoning drawer (`ThinkingSection`) for models outputting `<think>...</think>` tokens with ambient pulsing animation.
  - Message action toolbar: one-tap response copying, sharing, regeneration, and Text-to-Speech (TTS) audio playback.
  - Follow mode button with smooth touch-drag detection and non-conflicting auto-scrolling during streaming.
- 📐 **Offline-First Canvas Mermaid & Rich Content**:
  - 100% offline, native Android Canvas-based Mermaid diagram rendering (`MermaidDiagramView`) supporting flowcharts, sequence diagrams, class diagrams, and state diagrams without WebViews or network access.
  - Dark syntax-highlighted code blocks with line numbers, language labels, and one-tap clipboard copy.
  - LaTeX math equation rendering (`MathEquationView`) and structured markdown tables (`MarkdownTable`).
- 🐬 **Dual-Engine Architecture (LiteRT-LM + llama.cpp)**:
  - Hardened JNI bridge (`dolphin_gguf.cpp` / `DolphinNativeEngine`) to run **Dolphin 3.0 Qwen2.5 3B** (`Q4_K_M`) and less-restricted **Huihui Qwen3.5 Abliterated** models with native thread safety.
  - Unified Google **LiteRT-LM** engine for Google AI Edge models, **Qwen 2.5 Instruct**, and **DeepSeek-R1 Distill**.
  - Multimodal Vision-Language (VLM) support with **Qwen2-VL-2B** for OCR and visual inspection.
- 🗄️ **SQLite v3 & Long-Term Memory Consolidation**:
  - Full multi-session persistent chat storage with Write-Ahead Logging (WAL) and foreign key cascade deletion.
  - Autonomous cross-session long-term memory engine (`MemoryManager`) that extracts, indexes, pins, and manages durable user facts and preferences.
- 🌡️ **Adaptive Thermal & Memory Governors**:
  - Real-time Android OS thermal status monitoring (`PowerManager.OnThermalStatusChangedListener`) with dynamic CPU thread throttling across big.LITTLE clusters.
  - Pre-flight RAM safety estimation (`MemoryMonitor` and `GgufMetadataParser`) preventing out-of-memory crashes before loading oversized models.
  - Configurable performance profiles: **Eco Mode** (battery priority), **Balanced Mode** (default), and **Performance Mode** (sustained throughput).
- 🔓 **Configurable Unrestricted Mode & Prompt Profiles**:
  - Easily toggle Unrestricted Mode with customizable system prompts, ChatML template sanitization, and fine-grained inference controls (Temperature, Top-P, Repetition Penalty, Context Budgeting).
- 📊 **Real-Time Telemetry Badge**:
  - Live tokens-per-second (TPS), time-to-first-token (TTFT in ms), active execution backend, token count, and elapsed time.
- 📂 **Advanced Model Hub & Storage Telemetry**:
  - Import `.gguf` and `.litertlm` models directly from device storage via Android Storage Access Framework (SAF).
  - Built-in Model Catalog featuring verified Hugging Face download links, quantization details, and recommended RAM requirements.

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
│   │   └── ChatDatabaseHelper.kt        // Persistent SQLite store (Schema v3, WAL mode)
│   ├── preferences/
│   │   └── AppPreferences.kt            // Settings & inference parameter persistence
│   └── repository/
│       └── ConversationRepository.kt    // Data access layer with memory integration
├── domain/
│   ├── context/
│   │   ├── PromptContextBuilder.kt      // Context window sliding & token budgeting
│   │   └── PromptValidation.kt          // ChatML sanitization & injection defense
│   ├── engine/
│   │   ├── InferenceEngine.kt           // Unified inference contract
│   │   ├── LiteRTInferenceEngine.kt     // LiteRT-LM backend (Qwen 2.5, Qwen2-VL)
│   │   ├── LlamaCppInferenceEngine.kt   // llama.cpp GGUF backend (Dolphin, Abliterated)
│   │   ├── InferenceEngineFactory.kt    // Dynamic backend router
│   │   └── GgufMetadataParser.kt        // GGUF header parser & RAM estimator
│   ├── memory/
│   │   └── MemoryManager.kt             // Autonomous cross-session fact consolidation
│   ├── modelmanager/
│   │   ├── ModelCatalogItem.kt          // Verified model catalog
│   │   └── ModelRepository.kt           // Discovery & SAF file import
│   ├── performance/
│   │   ├── ThermalPerformanceManager.kt // PowerManager thermal callbacks & battery telemetry
│   │   └── MemoryMonitor.kt             // ActivityManager RAM inspection & OOM guard
│   └── tts/
│       └── TtsManager.kt                // Text-to-speech audio synthesis engine
├── inference/
│   └── DolphinNativeEngine.kt           // Hardened JNI bridge to libdolphin_gguf.so
├── model/
│   ├── ChatMessage.kt                   // Strongly typed chat messages & benchmarks
│   ├── MemoryModel.kt                   // Extracted long-term memory entries
│   └── ModelInfo.kt                     // Model metadata & backend types
├── ui/
│   ├── components/
│   │   ├── BenchmarkBadge.kt            // Telemetry badge (TPS, TTFT, Backend)
│   │   ├── ChatComposer.kt              // Floating message composer with Stop action
│   │   ├── MarkdownTable.kt             // Structured tabular markdown renderer
│   │   ├── MarkdownText.kt              // Markdown renderer with syntax-highlighted code
│   │   ├── MathEquationView.kt          // Offline LaTeX math expression renderer
│   │   ├── MemoryManagerDialog.kt       // Persistent memory inspector & editor
│   │   ├── MermaidDiagramView.kt        // 100% offline Canvas Mermaid renderer
│   │   ├── ModelManagerDialog.kt        // Multi-tab model manager & catalog
│   │   ├── NavigationDrawer.kt          // Sovereign sidebar with date grouping & search
│   │   ├── SettingsDialog.kt            // Grouped configuration dialog
│   │   └── ThinkingSection.kt           // Reasoning drawer (<think>...</think>)
│   ├── designsystem/
│   │   ├── SovereignButtons.kt          // Tactile button primitives
│   │   ├── SovereignControls.kt         // Toggles, sliders, and checkboxes
│   │   ├── SovereignDialogsAndSheets.kt // Modal dialogs and adaptive bottom sheets
│   │   ├── SovereignFeedback.kt         // Banners, toasts, and pulsing animations
│   │   ├── SovereignIndicators.kt       // Loading indicators and badges
│   │   ├── SovereignInputs.kt           // Styled text inputs and search bars
│   │   ├── SovereignSurfaces.kt         // Elevated cards, glassmorphic containers
│   │   └── SovereignTokens.kt           // Design token definitions & color palettes
│   ├── theme/
│   │   └── Theme.kt                     // Sovereign dark/light theme integration
│   └── ChatScreen.kt                    // Main host screen & gesture management
└── MainActivity.kt
```

---

## 📄 License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
