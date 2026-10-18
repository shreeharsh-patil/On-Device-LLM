## What's New in v1.1.1 🚀

### 🛠️ Installation & Compatibility Fixes
- **Target SDK Compatibility**: Reduced `targetSdk` from experimental preview `36` to stable `35` (Android 15), completely resolving the `"App not installed as package appears to be invalid"` error on Android 12, 13, 14, and 15 devices.
- **Native Library Extraction**: Configured `android:extractNativeLibs="true"` in `AndroidManifest.xml` and legacy packaging in Gradle to ensure 100% reliable `.so` loading across all device architectures without page-alignment issues.
- **Permanent Release Keystore**: Signed with dedicated release keystore supporting APK Signature Scheme v2 & v3.
- *(Note: If updating from an earlier test build, please uninstall any previously installed version first to avoid certificate conflicts).*

### 🧠 Native GGUF Inference (llama.cpp)
- **Packaged Native Engine**: `libdolphin_gguf.so` bundled directly into the APK for `arm64-v8a` and `x86_64`.
- **Offline & Self-Contained**: Statically linked with `-DANDROID_STL=c++_static` to eliminate external runtime dependencies.
- **Support for Less-Restricted Models**: Native support for **Dolphin 3.0 Qwen2.5 3B** (`Q4_K_M`) and **Qwen3.5 Abliterated** GGUF models.

### 🔍 Deep Model Discovery & Scoped Storage Recovery
- **Recursive Multi-Directory Scan**: Automatically scans internal files, app external directories, public Downloads, Documents, SD cards, and Android MediaStore.
- **Magic Bytes Validation**: Detects extensionless or `.bin` files via header magic bytes (`GGUF` at offset 0, `TFL3` at offset 4).
- **Persistent Model Registry**: Discovered and imported models persist reliably across app backgrounding, reboots, and updates.

### 🎨 Brand & UI Polish
- **Futuristic Monochrome AI Robot Emblem**: Integrated across adaptive launcher icons, round icons, drawer header, and empty chat state.
