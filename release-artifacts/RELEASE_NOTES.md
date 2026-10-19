## What's New in v1.3.0 🔓⚡

### 🔓 Configurable Unrestricted Mode & Prompt Profiles
- **Mode Toggle**: Effortlessly toggle between **Standard Mode** and **Unrestricted Mode** directly from the chat header or Settings dialog.
- **Custom Instruction Profiles**: Create, edit, save, reset, and switch custom instruction profiles tailored for technical research, programming, cybersecurity education, robotics, and reverse engineering.
- **Granular Inference Tuning**:
  - Temperature (`0.0` - `2.0`)
  - Top-P sampling (`0.0` - `1.0`)
  - Repetition penalty (`1.0` - `2.0`)
  - Max generation tokens & context window sizing
  - Custom stop sequence handling
- **Prompt Sanitization & Template Integrity**: Automatically strips accidental raw role tokens from user-entered instructions to prevent prompt injection or ChatML format corruption.
- **Honest Runtime Transparency**: Documents which behaviors are configurable via prompt/sampling vs. inherent model weight alignment constraints.

### ⚡ Responsive Chat & Smooth Touch Gestures
- **Non-Conflicting Gesture Scrolling**: Auto-scroll intelligently detects user touch drag gestures (`isUserDragging`), completely eliminating stutter and jitter when reading earlier conversation turns during active generation.
- **O(1) Message State Updates**: Replaced full-list reallocations on every streamed token chunk with efficient in-place updates, drastically reducing garbage collection pressure.
- **Throttled UI Dispatch**: Tuned StateFlow emission cadence to guarantee consistent 60–120 FPS Compose rendering even during rapid native llama.cpp / LiteRT token bursts.

### 📊 Accurate Token Counting & Real-Time Benchmarks
- **LiteRT Multi-Token Chunk Resolution**: Corrected token accounting where streaming string chunks were previously counted as single tokens.
- **Real-Time Token Tracking**: Displays live token count during generation (`X tok`) and a full breakdown upon turn completion (`X tok (Y total) • Z.Zs`).
- **Engine Parity**: Consistent prefill + decode metric reporting across both LiteRT-LM and native llama.cpp engines.

---

### 📦 Checksums & Verification
- **APK**: `OnDeviceLLM-v1.3.0.apk`
- **SHA-256**: `caee43a1c2f180d4e34109595365d749c6d40c29a36fe089cd85951728a472e5`
- **Signing**: APK Signature Scheme v2 & v3 verified
