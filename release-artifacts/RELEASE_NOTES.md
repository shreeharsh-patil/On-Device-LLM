## What's New in v1.2.0 🧠🚀

### 🧠 Context-Aware Conversational Intelligence
- **Multi-Turn Context Window**: Resolves follow-up queries (*"explain that again"*, *"what about the second option?"*, *"continue from where we stopped"*) by retaining ordered turn history.
- **Dynamic Context Budgeting**: Safely budgets prompt space reserving required output tokens (`maxOutputTokens`), truncating oldest context turns and injecting summaries when context limits are reached.
- **Strict Role Formatting**: Centralized ChatML serialization with proper `<|im_start|>` and `<|im_end|>` markers.
- **Conversation Isolation**: Cleanly resets engine context when switching chats or starting a new session to prevent context leakage.

### 💾 Persistent Cross-Chat Memory
- **Local-First SQLite Memory Storage**: Retains user preferences, project architectures, and episodic discussions across different conversation sessions and application reboots.
- **Categorized Memory Architecture**: Segregates memories into User Profile (`USER`), Active Projects & Technical Decisions (`PROJECT`), Past Discussions (`EPISODIC`), and Long Conversation Summaries.
- **Offline Relevance Search & Ranking**: Real-time keyword scoring, pinned boosting, and recency decay ranking without external embeddings or cloud dependencies.
- **Local Consolidation & Sanitization**: Regex filtering rejects passwords, bearer tokens, and API keys; deduplication updates existing facts instead of creating contradictory duplicates.
- **Full In-App Memory Management**: Dedicated UI dialog with search bar, category chips, manual add/edit, item deletion, full memory reset, and a master enable/disable toggle.

### 🛑 Elimination of Runaway Generation & Repetition Loops
- **Native llama.cpp Penalty Samplers**: Integrated `llama_sampler_init_penalties(64, 1.15f, 0.05f, 0.05f)` into the C++ sampling pipeline.
- **ChatML Stop Token Enforcement**: Native stop detection on `<|im_end|>`, `<|endoftext|>`, and `<|im_start|>`.
- **Heuristic Loop Breakers**: Terminating on 5 consecutive identical tokens or 8-token cyclical repetition patterns.
- **Authoritative Generation IDs**: Eliminates stale chunks from canceled or previous turns.
- **Safe Regeneration**: Deletes previous assistant response without duplicating user prompts in database or UI.

### 📜 Smooth Scrolling & Follow-Mode State Machine
- **Smart Follow-Mode**: Automatically follows new tokens when at the bottom, but immediately suspends auto-scroll when user scrolls upward to read earlier messages.
- **"Jump to Latest" Indicator**: Floating pill button with pulsing activity indicator allows jumping back to the live response at any time.

---

### 📦 Checksums & Verification
- **APK**: `OnDeviceLLM-v1.2.0.apk`
- **SHA-256**: `689ac8febb0fb1d49e192763a22505e5f3288af6d5c16a98150b9465993ba573`
- **Signing**: APK Signature Scheme v2 & v3 verified
