# Release Notes: On-Device LLM v1.4.0 🚀🛡️

> **Release Codename**: Sovereign Design System & Dual-Engine Production Hardening  
> **Version**: `1.4.0` (versionCode `8`)  
> **Branch**: `main` (merged from `redesign/ui-ux-overhaul`)  
> **Release Date**: November 15, 2026  
> **Security Posture**: 100% Offline-First | Zero Network Permissions | Hardware-Enforced Privacy

---

## 🌟 Executive Summary

**On-Device LLM v1.4.0** is our largest milestone release to date. It completely redesigns the user experience with the **Sovereign Design System**, introduces **100% offline-first Canvas-based Mermaid diagram rendering**, executes a comprehensive **SQLite v3 migration with persistent long-term memory consolidation**, hardens our **dual-engine architecture (LiteRT-LM & llama.cpp)** with native JNI thread safety and zero memory leaks, and implements intelligent **thermal and memory governors** for sustained, power-efficient mobile AI performance.

---

## 🔬 Detailed Phase-by-Phase Improvements

### 🎨 Phase 1: Sovereign Design System Foundation & Security Contracts
- **Sovereign Design System Primitives**: Established an industrial-grade design token hierarchy featuring Cyberpunk Onyx, Deep Void, Platinum typography, Radiant Cyan accents, Amber warnings, and Crimson alert tokens across dark and light palettes.
- **Composable Component Library**: Built a complete suite of Sovereign UI foundations (`SovereignSurfaces`, `SovereignButtons`, `SovereignInputs`, `SovereignControls`, `SovereignIndicators`, `SovereignFeedback`, `SovereignDialogsAndSheets`).
- **100% Offline-First Security Enforcement**:
  - Completely excluded `android.permission.INTERNET` from both application source and merged release manifests.
  - Configured `data_extraction_rules.xml` and `backup_rules.xml` to prevent any SQLite database, WAL file, SharedPreferences, or model weights from leaking to Android Cloud Backup or adb backup.
  - Set `android:allowBackup="false"` and scoped storage permissions to comply strictly with modern Android SAF security.
- **Hardened Release Build Configuration**:
  - Eliminated tracked keystore credentials from Git history; established robust fallback debug signing for automated local release builds.
  - Hardened R8 / ProGuard shrinkage rules (`rules.keep`) preserving LiteRT-LM, llama.cpp JNI bridges, and Kotlin reflection/serialization without breaking native runtime execution.
- **Strongly Typed Domain Contracts**: Upgraded `ModelInfo`, `ChatMessage`, `InferenceEngine`, and `ModelCatalogItem` domain contracts with rich telemetry, backend tags, and memory metadata.

### 🧭 Phase 2: Navigation Drawer & Adaptive Multi-Session Workspace
- **Sovereign Navigation Drawer**: Fluid slide-out drawer styled with Sovereign elevated surfaces, glowing selection borders, and edge-to-edge support.
- **Intelligent Temporal Grouping**: Grouped multi-turn conversation sessions into clear chronological buckets:
  - *Today*
  - *Yesterday*
  - *Previous 7 Days*
  - *Older*
- **Session Management Tools**:
  - Live fuzzy search filtering across all conversation titles.
  - One-tap inline title editing and renaming.
  - Pinning prioritized conversations to the top of the drawer.
  - Animated deletion with confirmation dialogs and foreign key cascade cleanup.
- **Adaptive Screen Layouts**: Dynamic sidebar support that seamlessly switches between a temporary drawer on compact phones and an expanded persistent drawer on tablets and foldables.

### 📦 Phase 3: Model Manager, Hub Discovery & Hardware RAM Estimation
- **Dual-Pane Sovereign Model Hub**: Refactored `ModelManagerDialog` into a streamlined two-tab experience:
  - **Installed Models**: Comprehensive management of local `.gguf` and `.litertlm` models with active badges, file paths, and deletion safety.
  - **Model Hub (Verified Catalog)**: Instant discovery for Dolphin 3.0 Qwen2.5 3B, Qwen 2.5 Coder 3B/7B Abliterated, Qwen 2.5 1.5B Instruct, Qwen2-VL Multimodal Vision, and DeepSeek-R1 Distill.
- **Pre-Flight RAM Safety Estimator**: Integrated GGUF header parsing (`GgufMetadataParser`) with live `ActivityManager` telemetry to calculate weight footprint + KV cache allocation before loading, proactively preventing Out-Of-Memory (OOM) app crashes.
- **Storage Telemetry & Multi-Format Filtering**: Visual breakdown of device storage, model directory consumption, and one-touch SAF file import from any internal storage folder.

### ⚙️ Phase 4: Sovereign Settings & Persistent Long-Term Memory
- **Redesigned Settings Dialog**: Modular layout grouping inference parameters, system prompts, hardware acceleration, and memory settings.
- **Long-Term Memory Management**: Introduced `MemoryManagerDialog` and `MemoryManager` to manage autonomous memory consolidation:
  - Automatically extracts durable user facts, preferences, and technical requirements across conversations.
  - Visual memory inspector with confidence scores, category tags, timestamp tracking, and manual editing/deletion.
  - Pin important memory entries to guarantee inclusion in prompt context.
- **Fine-Grained Inference Sampling**: Real-time adjustment for Temperature (`0.0 - 2.0`), Top-P (`0.0 - 1.0`), Repetition Penalty (`1.0 - 2.0`), Max Generation Tokens, and context window lengths.
- **Unrestricted Mode & ChatML Prompt Profiles**: Instant toggle for Unrestricted Mode with customizable system prompts, role template sanitization, and protection against accidental prompt injection.

### 📊 Phase 5: Rich Content Rendering & Offline-First Canvas Mermaid Diagrams
- **100% Offline Canvas Mermaid Engine**: Pure Kotlin/Canvas and Compose rendering of Mermaid diagrams (`MermaidDiagramView`) without WebViews, JavaScript engines, or internet connections:
  - **Flowcharts**: Directed graphs (`flowchart TD`, `flowchart LR`, `graph TD`, `graph LR`) with subgraphs, shapes (rectangles, rounded, diamonds, circles), and link labels.
  - **Sequence Diagrams**: Actor lifelines, solid/dashed arrows, notes, loops, and alt/opt blocks.
  - **Class Diagrams**: Class boxes, member methods/fields, and inheritance relationships.
  - **State Diagrams**: State transitions, composite states, and terminal nodes.
- **Syntax-Highlighted Markdown**: Low-latency rendering in `MarkdownText` with dark syntax themes, code block line numbers, language badges, and one-tap clipboard copy.
- **LaTeX Math & Tables**: High-fidelity rendering of math expressions via `MathEquationView` and structured tabular data via `MarkdownTable`.

### 💬 Phase 6: Core Chat Interface, Sovereign Composer & Live Telemetry
- **Sovereign Chat Interface**: Revamped `ChatScreen` with edge-to-edge layout, modern speech bubbles, and smooth state transitions.
- **Floating Intelligent Composer**: Expandable input bar with multi-line auto-grow, multimodal image attachment chips with thumbnails, inline clear actions, and an immediate **Stop Generation** button.
- **Voice Input & Text-to-Speech (TTS)**: Integrated `TtsManager` allowing one-tap audio playback of AI responses with playback indicators and stop controls.
- **Non-Conflicting Gesture Scrolling & Follow Mode**: Smart user drag detection (`isUserDragging`) that releases auto-scroll when the user reads history, accompanied by a floating **Follow Mode** button to snap back to live streaming tokens.
- **Collapsible Reasoning Drawer**: High-tech `<think>...</think>` drawer with glowing pulse animations for deep-reasoning models.
- **Live Benchmark Telemetry**: Compact real-time badge displaying Time-To-First-Token (TTFT in ms), Decode Tokens Per Second (TPS), active engine backend, and total duration.

### ⚡ Phase 7: Data Layer Architecture, SQLite v3 Migration & Dual-Engine Native Hardening
- **SQLite v3 Migration (`ChatDatabaseHelper`)**:
  - Implemented database version 3 schema with Write-Ahead Logging (WAL) and foreign key cascade constraints.
  - Created dedicated tables for `memories` and `conversation_summaries` with composite indexes (`idx_messages_conv_time`).
  - Added columns for pinned conversations, previews, cancellation states, and rich performance metrics.
- **llama.cpp / Dolphin Native Engine Hardening**:
  - Rewrote JNI bridge in `dolphin_gguf.cpp` and `DolphinNativeEngine.kt` to eliminate concurrency race conditions, native handle leaks, and buffer bleed.
  - Implemented thread pinning optimized for ARM big.LITTLE architectures.
- **LiteRT-LM Engine Hardening**:
  - Streamlined token decoding and prefill/decode benchmark calculations.
  - Enhanced multimodal image preprocessing and vision token handling for Qwen2-VL.
- **Adaptive Thermal & Memory Governors**:
  - Proactive OS thermal status listeners (`PowerManager.OnThermalStatusChangedListener`) with automatic thread reduction during thermal throttling.
  - Dynamic memory tracking via `MemoryMonitor` to guard against OS-level background kills.
- **PromptContextBuilder**: Token budgeting and context window sliding algorithm to guarantee optimal prompt composition without exceeding engine limits.

---

## 🧪 Verification & Quality Assurance Audit

All verification checks were executed by `@verifier` on the release artifacts:

```text
Suite Breakdown:
  • ContractsModelTests:                  9/9 passed (100%)
  • ConversationContextAndPromptTests:    6/6 passed (100%)
  • DataAndPersistenceTests:              8/8 passed (100%)
  • DrawerGroupingTests:                  4/4 passed (100%)
  • EngineAndNativeHardeningTests:        9/9 passed (100%)
  • ExampleUnitTest:                      1/1 passed (100%)
  • GenerationDeduplicationAndStreamTests: 5/5 passed (100%)
  • ModelDiscoveryAndRecoveryTests:       9/9 passed (100%)
  • ModelManagerHelpersTests:             6/6 passed (100%)
  • OnDeviceQwenCoreTests:               11/11 passed (100%)
  • PersistentMemoryAndConsolidationTests:10/10 passed (100%)
  • RichContentParserTests:               8/8 passed (100%)
  • ScrollAndFollowModeTests:             3/3 passed (100%)
  • SettingsAndMemoryUiTests:             4/4 passed (100%)
  • SovereignDesignSystemTests:           4/4 passed (100%)
  • UnrestrictedModeAndPromptProfileTests:12/12 passed (100%)
------------------------------------------------------------------
Total Test Suites: 16 | Total Tests: 109 | Failures: 0 | Skipped: 0
```

---

## 📦 Release Artifacts & Checksums

| Artifact | File Size | SHA-256 Checksum |
|---|---|---|
| `release-artifacts/On-Device-LLM-v1.4.0.apk` | 15,684,445 bytes | `827682468e2da70bba8e478c87ebec9f9887f75f3790e981c7a8b0d24ff8e637` |
| `release-artifacts/OnDeviceLLM-v1.4.0.apk` | 15,684,445 bytes | `827682468e2da70bba8e478c87ebec9f9887f75f3790e981c7a8b0d24ff8e637` |

### Checksum Verification via CLI
```bash
# PowerShell
Get-FileHash -Algorithm SHA256 release-artifacts/On-Device-LLM-v1.4.0.apk

# Linux / macOS
sha256sum release-artifacts/On-Device-LLM-v1.4.0.apk
```
Expected SHA-256: `827682468e2da70bba8e478c87ebec9f9887f75f3790e981c7a8b0d24ff8e637`
