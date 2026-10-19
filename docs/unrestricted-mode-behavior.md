# Unrestricted Mode — Behavior and Limitations

This feature adds a configurable **Inference Mode** choice and a **Prompt Profile** editor
to the existing on-device Qwen chat app. It is intentionally framed as *configurable local
behavior*, not a universal jailbreak.

## What changes

- **Inference Mode toggle**
  - `Standard` uses the default Qwen system prompt unless a custom prompt profile is selected.
  - `Unrestricted` uses an alternative system prompt that asks the model to be direct,
    detailed, and to avoid unnecessary disclaimers or filler.
- **Prompt profiles**
  - The app ships with two built-in profiles: `Standard` and `Unrestricted`.
  - Users can save, select, reset, and delete custom profiles.
  - Each profile stores `instructionText` and `effectiveSystemPrompt`.
  - The active profile determines the system instruction sent for new conversations,
    continued conversations, and regenerated responses.
- **Inference parameter validation**
  - Temperature, top-p, repetition penalty, max output tokens, context length, thread count,
    stop sequences, and system prompt are validated before generation.
  - Out-of-range or oversized values produce clear validation errors with the offending key.
  - The app also records which parameters are **accepted by the app but not yet forwarded to
    the runtime**, so users know what is validated vs what is actually honored.

## How it is implemented

- `GenerationConfig` now carries:
  - `unrestrictedMode: UnrestrictedMode`
  - `activePromptProfileId: String`
- `PromptContextBuilder.buildChatMl` uses the **active prompt profile** as the system block
  instead of blindly appending `config.systemPrompt` on top of another system prompt.
- ChatML formatting is preserved:
  - `<|im_start|>system\n...\n<|im_end|>`
  - followed by prior turns in `<|im_start|>user/assistant\n...\n<|im_end|>` form
  - ending with `<|im_start|>assistant\n`
- System-block content is sanitized once before use:
  - trimmed and length-capped
  - embedded ChatML role markers removed
  - excessive blank lines collapsed
- For llama.cpp-backed GGUF inference, prompts already containing `<|im_start|>` are used as-is.

## Configurable vs inherent restrictions

### Configurable at the app/prompt level

- System instruction text
- Active prompt profile
- Selected inference mode
- Temperature, top-p, repetition penalty, max output tokens, context length, thread count,
  stop sequences, and system prompt length are validated by the app
- Memory retrieval, conversation history, and streaming remain active in both modes

### Still subject to model and runtime behavior

- Anything encoded in the model weights or fine-tuning
- Restrictions or tendencies introduced by the model's training data and alignment process
- Constraints imposed by the inference wrapper or native runtime configuration
- Failures caused by malformed prompts, unsupported parameters, or incorrect stopping logic

In other words: changing the system prompt can change **application-level behavior**, but it
does not remove everything the model may refuse based on its own training or the local backend.

## Supported inference parameters

The app exposes and validates these parameters:

| Parameter | What it controls | Notes |
|---|---|---|
| System prompt / instruction template | System block content | Applied through ChatML for GGUF paths or LiteRT `ConversationConfig` |
| Temperature | Sampling randomness | Validated `0.0..2.0` |
| Top-p | Nucleus sampling cutoff | Validated `0.0..1.0` |
| Repetition penalty | Repetition control | Validated `0.1..2.0` |
| Maximum generation tokens | Max tokens to generate | Validated `1..8192` |
| Stop sequences / end-of-turn tokens | Additional stop strings | Validated count and total length |
| Context window | Context length used for loading/budgeting | Validated within app limits |
| CPU threads | Thread count for GGUF path | Validated within app limits |

Not all of these are forwarded to every backend. The current implementation reports which
parameters are **supported at runtime** versus which are **validated but effectively fixed** by
the backend. Do not assume a parameter is honored just because it is accepted by the app.

## Chat template handling

The app uses the model's ChatML-style template path already present in the codebase.
It does not casually replace role tokens or strip special tokens.

Custom instructions are injected only into the system block. The rest of the conversation
structure stays intact so messages, memory, and streaming remain well-formed.

## Memory and conversation isolation

- Persistent cross-chat memory retrieval runs in both modes.
- Conversation history is scoped by conversation ID.
- Prompt profiles and mode are per-user settings, not per-message hidden state.
- Switching chats does not merge history or prompt text from another conversation.

## Acceptance criteria addressed

- Switching modes changes the system prompt used for inference via the active prompt profile.
- The selected mode and active profile persist through app restart via app preferences.
- Custom system prompts render through the supported chat template without breaking it.
- Memory retrieval continues in both modes.
- Conversation switching keeps history isolated.
- Stop generation and scrolling behavior are unchanged by this feature.
- Unsupported or out-of-range inference parameters produce explicit validation errors.

## Local evaluation

Run the project unit tests to exercise the core logic:
- Prompt profile selection and persistence
- Standard vs unrestricted prompt construction
- ChatML rendering with custom profiles
- Memory injection in both modes
- History isolation between conversations
- Validation errors for invalid parameters
- Regeneration config preservation

Because this runs fully offline on-device, the best local test is:
1. Switch to Unrestricted mode.
2. Send a direct technical question.
3. Compare the prompt construction and resulting behavior against Standard mode.
4. Confirm that the active mode and profile persist after restarting the app.

## Known honest limitation

This feature increases user control over app-level prompting and configuration. It does not
guarantee that the model will answer every request, and it does not remove restrictions that
originate in the model itself, the local runtime, or the inference wrapper.
