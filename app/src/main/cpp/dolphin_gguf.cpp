// Thread-safe CPU llama.cpp JNI bridge for GGUF models.
// Provides session isolation, native abort callbacks, KV prefix cache reuse,
// greedy/top-k/min-p/top-p/temperature sampling, and UTF-8 / stop-sequence holdback streaming.
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include "llama.h"

namespace {

constexpr int kMinContext = 1024;
constexpr int kMaxContext = 8192;
constexpr int kMaxThreads = 8;
constexpr int kMaxOutputTokens = 4096;
constexpr int kDecodeBatchSize = 256;

struct Session {
    llama_model* model = nullptr;
    llama_context* context = nullptr;
    std::mutex mutex;
    std::atomic<bool> abort_requested{false};
    std::vector<llama_token> cached_tokens;

    ~Session() {
        if (context) {
            llama_free(context);
            context = nullptr;
        }
        if (model) {
            llama_free_model(model);
            model = nullptr;
        }
    }
};

std::mutex g_sessions_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> g_sessions;
std::atomic<jlong> g_next_handle{1};

std::shared_ptr<Session> find_session(jlong handle) {
    if (handle <= 0) return nullptr;
    std::lock_guard<std::mutex> lock(g_sessions_mutex);
    auto it = g_sessions.find(handle);
    return it != g_sessions.end() ? it->second : nullptr;
}

bool abort_callback(void* user_data) {
    if (!user_data) return false;
    auto* session = static_cast<Session*>(user_data);
    return session->abort_requested.load(std::memory_order_relaxed);
}

void throw_java(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}

// Returns the number of trailing bytes in `buf[0..len)` that form an incomplete
// multi-byte UTF-8 sequence, so callers never split a codepoint across emissions.
size_t trailing_incomplete_utf8_bytes(const std::string& buf, size_t len) {
    if (len == 0) return 0;
    const auto* data = reinterpret_cast<const uint8_t*>(buf.data());
    if (data[len - 1] < 0x80) return 0;

    size_t scan = 0;
    while (scan < 3 && scan + 1 < len && (data[len - 1 - scan] & 0xC0) == 0x80) {
        scan++;
    }
    const size_t lead_idx = len - 1 - scan;
    const uint8_t lead = data[lead_idx];
    const size_t have = len - lead_idx;

    size_t expected = 1;
    if ((lead & 0xE0) == 0xC0) {
        expected = 2;
    } else if ((lead & 0xF0) == 0xE0) {
        expected = 3;
    } else if ((lead & 0xF8) == 0xF0) {
        expected = 4;
    } else {
        // Standalone continuation or invalid lead byte: do not wedge future flushes.
        return 0;
    }
    return have < expected ? have : 0;
}

bool emit_bytes(JNIEnv* env, jobject listener, jmethodID emit, const char* data, size_t len) {
    if (len == 0) return true;
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(len));
    if (!bytes) return false;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
    jboolean keep_going = env->CallBooleanMethod(listener, emit, bytes);
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck() || keep_going != JNI_TRUE) {
        return false;
    }
    return true;
}

std::vector<std::string> build_stop_list(JNIEnv* env, jobjectArray extra_stops) {
    std::vector<std::string> stops = {
        "<|im_end|>",
        "<|endoftext|>",
        "<|im_start|>",
        "<|eot_id|>",
        "<end_of_turn>",
        "<|end|>"
    };
    if (extra_stops != nullptr) {
        const jsize n = env->GetArrayLength(extra_stops);
        for (jsize i = 0; i < n; ++i) {
            auto jstr = static_cast<jstring>(env->GetObjectArrayElement(extra_stops, i));
            if (!jstr) continue;
            const char* cstr = env->GetStringUTFChars(jstr, nullptr);
            if (cstr != nullptr) {
                std::string s(cstr);
                env->ReleaseStringUTFChars(jstr, cstr);
                if (!s.empty() && std::find(stops.begin(), stops.end(), s) == stops.end()) {
                    stops.push_back(std::move(s));
                }
            }
            env->DeleteLocalRef(jstr);
        }
    }
    return stops;
}

jlong native_open(JNIEnv* env, jobject, jstring path, jint context_size, jint threads) {
    if (path == nullptr) {
        throw_java(env, "Missing GGUF model path");
        return 0;
    }
    static std::once_flag init_flag;
    std::call_once(init_flag, [] { llama_backend_init(); });

    const char* utf_path = env->GetStringUTFChars(path, nullptr);
    if (!utf_path) return 0;

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU-only in this build
    model_params.use_mmap = true;
    model_params.use_mlock = false;
    llama_model* model = llama_load_model_from_file(utf_path, model_params);
    env->ReleaseStringUTFChars(path, utf_path);
    if (!model) {
        throw_java(env, "Unable to load GGUF weights (corrupt format or insufficient memory)");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::clamp(static_cast<int>(context_size), kMinContext, kMaxContext));
    ctx_params.n_batch = kDecodeBatchSize;
    ctx_params.n_ubatch = 128;
    ctx_params.n_threads = std::clamp(static_cast<int>(threads), 1, kMaxThreads);
    ctx_params.n_threads_batch = ctx_params.n_threads;

    llama_context* context = llama_new_context_with_model(model, ctx_params);
    if (!context) {
        llama_free_model(model);
        throw_java(env, "Unable to allocate GGUF context window; try lowering context size or using smaller quantization");
        return 0;
    }

    auto session = std::make_shared<Session>();
    session->model = model;
    session->context = context;
    llama_set_abort_callback(context, abort_callback, session.get());

    const jlong handle = g_next_handle.fetch_add(1, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        g_sessions.emplace(handle, std::move(session));
    }
    return handle;
}

void native_close(JNIEnv*, jobject, jlong handle) {
    std::shared_ptr<Session> session;
    {
        std::lock_guard<std::mutex> lock(g_sessions_mutex);
        auto it = g_sessions.find(handle);
        if (it != g_sessions.end()) {
            session = std::move(it->second);
            g_sessions.erase(it);
        }
    }
    if (!session) return;
    // Wake any active decode loop before waiting for the session mutex.
    session->abort_requested.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> session_lock(session->mutex);
    session->cached_tokens.clear();
    // shared_ptr destructor frees context & model cleanly once no thread holds a reference.
}

void native_request_abort(JNIEnv*, jobject, jlong handle) {
    auto session = find_session(handle);
    if (session) {
        session->abort_requested.store(true, std::memory_order_relaxed);
    }
}

jboolean native_reset_context(JNIEnv*, jobject, jlong handle) {
    auto session = find_session(handle);
    if (!session) return JNI_FALSE;
    session->abort_requested.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(session->mutex);
    if (!session->context) return JNI_FALSE;
    llama_kv_cache_clear(session->context);
    session->cached_tokens.clear();
    session->abort_requested.store(false, std::memory_order_relaxed);
    return JNI_TRUE;
}

jint native_count_tokens(JNIEnv* env, jobject, jlong handle, jstring text) {
    auto session = find_session(handle);
    if (!session || !text) return -1;
    std::lock_guard<std::mutex> lock(session->mutex);
    if (!session->model) return -1;

    const char* utf = env->GetStringUTFChars(text, nullptr);
    if (!utf) return -1;
    const int len = static_cast<int>(std::strlen(utf));
    if (len == 0) {
        env->ReleaseStringUTFChars(text, utf);
        return 0;
    }

    std::vector<llama_token> scratch(std::max(32, len / 2 + 8));
    int count = llama_tokenize(session->model, utf, len, scratch.data(),
                               static_cast<int>(scratch.size()), false, true);
    if (count < 0) {
        scratch.resize(static_cast<size_t>(-count));
        count = llama_tokenize(session->model, utf, len, scratch.data(),
                               static_cast<int>(scratch.size()), false, true);
    }
    env->ReleaseStringUTFChars(text, utf);
    return count >= 0 ? count : -1;
}

jstring native_apply_chat_template(
        JNIEnv* env,
        jobject,
        jlong handle,
        jobjectArray roles,
        jobjectArray contents,
        jboolean add_assistant) {
    auto session = find_session(handle);
    if (!session || !roles || !contents) return nullptr;
    std::lock_guard<std::mutex> lock(session->mutex);
    if (!session->model) return nullptr;

    // Only apply llama.cpp's template formatter when the GGUF actually embeds tokenizer.chat_template;
    // otherwise let Kotlin's ChatTemplateFamily adapters handle architecture-specific formatting.
    char tmpl_probe[64];
    int tmpl_len = llama_model_meta_val_str(session->model, "tokenizer.chat_template",
                                            tmpl_probe, sizeof(tmpl_probe));
    if (tmpl_len <= 0) {
        return nullptr;
    }

    const jsize n_roles = env->GetArrayLength(roles);
    const jsize n_contents = env->GetArrayLength(contents);
    if (n_roles <= 0 || n_roles != n_contents) return nullptr;

    std::vector<std::string> role_storage;
    std::vector<std::string> content_storage;
    role_storage.reserve(static_cast<size_t>(n_roles));
    content_storage.reserve(static_cast<size_t>(n_roles));

    for (jsize i = 0; i < n_roles; ++i) {
        auto jrole = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto jcontent = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        std::string r_str;
        std::string c_str;
        if (jrole) {
            const char* cr = env->GetStringUTFChars(jrole, nullptr);
            if (cr) {
                r_str = cr;
                env->ReleaseStringUTFChars(jrole, cr);
            }
            env->DeleteLocalRef(jrole);
        }
        if (jcontent) {
            const char* cc = env->GetStringUTFChars(jcontent, nullptr);
            if (cc) {
                c_str = cc;
                env->ReleaseStringUTFChars(jcontent, cc);
            }
            env->DeleteLocalRef(jcontent);
        }
        role_storage.push_back(std::move(r_str));
        content_storage.push_back(std::move(c_str));
    }

    std::vector<llama_chat_message> messages(static_cast<size_t>(n_roles));
    size_t total_chars = 0;
    for (jsize i = 0; i < n_roles; ++i) {
        messages[i].role = role_storage[i].c_str();
        messages[i].content = content_storage[i].c_str();
        total_chars += role_storage[i].size() + content_storage[i].size() + 32;
    }

    std::vector<char> out_buf(std::max<size_t>(512, total_chars * 2 + 256));
    int32_t res = llama_chat_apply_template(
        session->model,
        nullptr,
        messages.data(),
        messages.size(),
        add_assistant == JNI_TRUE,
        out_buf.data(),
        static_cast<int32_t>(out_buf.size()));

    if (res < 0) return nullptr;
    if (res >= static_cast<int32_t>(out_buf.size())) {
        out_buf.resize(static_cast<size_t>(res) + 1);
        res = llama_chat_apply_template(
            session->model,
            nullptr,
            messages.data(),
            messages.size(),
            add_assistant == JNI_TRUE,
            out_buf.data(),
            static_cast<int32_t>(out_buf.size()));
        if (res < 0 || res >= static_cast<int32_t>(out_buf.size())) return nullptr;
    }
    out_buf[static_cast<size_t>(res)] = '\0';
    return env->NewStringUTF(out_buf.data());
}

jint native_generate(
        JNIEnv* env,
        jobject,
        jlong handle,
        jstring prompt,
        jint max_new_tokens,
        jfloat j_temp,
        jfloat j_top_p,
        jint j_top_k,
        jfloat j_min_p,
        jfloat j_rep,
        jint j_seed,
        jobjectArray stop_sequences,
        jobject listener) {
    auto session = find_session(handle);
    if (!session || !prompt || !listener) {
        throw_java(env, "No GGUF model loaded");
        return -1;
    }

    jclass listener_class = env->GetObjectClass(listener);
    jmethodID emit = listener_class ? env->GetMethodID(listener_class, "onBytes", "([B)Z") : nullptr;
    if (listener_class) env->DeleteLocalRef(listener_class);
    if (!emit) return -1;

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    if (!input) return -1;
    std::string text(input);
    env->ReleaseStringUTFChars(prompt, input);

    const std::vector<std::string> stops = build_stop_list(env, stop_sequences);

    std::lock_guard<std::mutex> lock(session->mutex);
    if (!session->model || !session->context) {
        throw_java(env, "GGUF session has already been closed");
        return -1;
    }
    session->abort_requested.store(false, std::memory_order_relaxed);

    const int n_ctx = static_cast<int>(llama_n_ctx(session->context));
    const int requested_max = std::clamp(static_cast<int>(max_new_tokens), 1, kMaxOutputTokens);
    const int n_batch_cap = std::max(1, static_cast<int>(llama_n_batch(session->context)));

    std::vector<llama_token> tokens(std::max(128, static_cast<int>(text.size()) / 2 + 16));
    int count = llama_tokenize(session->model, text.c_str(), static_cast<int>(text.size()),
                               tokens.data(), static_cast<int>(tokens.size()), false, true);
    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(session->model, text.c_str(), static_cast<int>(text.size()),
                               tokens.data(), static_cast<int>(tokens.size()), false, true);
    }
    if (count <= 0) {
        throw_java(env, "Failed to tokenize GGUF prompt");
        return -1;
    }
    tokens.resize(static_cast<size_t>(count));

    const int max_output = std::min(requested_max, n_ctx - count - 4);
    if (max_output <= 0) {
        throw_java(env, "Conversation exceeds the GGUF context window. Start a new chat or shorten history.");
        return -1;
    }

    // Reuse longest cached KV prefix; leave >=1 token to re-evaluate for fresh logits.
    size_t common_len = 0;
    const size_t max_match = std::min(session->cached_tokens.size(), tokens.size() - 1);
    while (common_len < max_match && session->cached_tokens[common_len] == tokens[common_len]) {
        common_len++;
    }
    if (common_len > 0) {
        if (!llama_kv_cache_seq_rm(session->context, 0, static_cast<llama_pos>(common_len), -1)) {
            llama_kv_cache_clear(session->context);
            common_len = 0;
        }
    } else {
        llama_kv_cache_clear(session->context);
    }

    // Track exact tokens residing in KV seq 0 so mid-turn abort or failure keeps cache state valid.
    session->cached_tokens.assign(tokens.begin(), tokens.begin() + static_cast<ptrdiff_t>(common_len));

    for (size_t pos = common_len; pos < tokens.size(); pos += static_cast<size_t>(n_batch_cap)) {
        if (session->abort_requested.load(std::memory_order_relaxed)) {
            return 0;
        }
        const int n = static_cast<int>(std::min<size_t>(static_cast<size_t>(n_batch_cap), tokens.size() - pos));
        llama_batch batch = llama_batch_get_one(tokens.data() + pos, n);
        const int rc = llama_decode(session->context, batch);
        if (rc != 0) {
            llama_kv_cache_clear(session->context);
            session->cached_tokens.clear();
            if (session->abort_requested.load(std::memory_order_relaxed)) {
                return 0;
            }
            throw_java(env, "GGUF prompt processing failed");
            return -1;
        }
        session->cached_tokens.insert(
            session->cached_tokens.end(),
            tokens.begin() + static_cast<ptrdiff_t>(pos),
            tokens.begin() + static_cast<ptrdiff_t>(pos + n));
    }

    const float temperature = std::clamp(static_cast<float>(j_temp), 0.0f, 2.0f);
    const float top_p = std::clamp(static_cast<float>(j_top_p), 0.05f, 1.0f);
    const int top_k = std::clamp(static_cast<int>(j_top_k), 0, 200);
    const float min_p = std::clamp(static_cast<float>(j_min_p), 0.0f, 1.0f);
    const float rep_penalty = std::clamp(static_cast<float>(j_rep), 0.1f, 2.0f);
    const uint32_t seed = (j_seed >= 0) ? static_cast<uint32_t>(j_seed) : LLAMA_DEFAULT_SEED;

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (rep_penalty != 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, rep_penalty, 0.05f, 0.05f));
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        if (top_k > 0) {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
        if (min_p > 0.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_min_p(min_p, 1));
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));
    }

    int generated = 0;
    std::vector<llama_token> history_tokens;
    history_tokens.reserve(static_cast<size_t>(max_output));
    std::string pending;
    bool hit_stop = false;

    for (; generated < max_output; ++generated) {
        if (session->abort_requested.load(std::memory_order_relaxed)) {
            break;
        }

        const llama_token token = llama_sampler_sample(sampler, session->context, -1);
        llama_sampler_accept(sampler, token);
        if (llama_token_is_eog(session->model, token)) {
            break;
        }

        char stack_buffer[256];
        int size = llama_token_to_piece(session->model, token, stack_buffer, sizeof(stack_buffer), 0, false);
        std::vector<char> extra;
        const char* piece = stack_buffer;
        if (size < 0) {
            extra.resize(static_cast<size_t>(-size));
            size = llama_token_to_piece(session->model, token, extra.data(), static_cast<int>(extra.size()), 0, false);
            piece = extra.data();
        }

        if (size > 0) {
            pending.append(piece, static_cast<size_t>(size));

            // Check full stop-sequence matches first, choosing the earliest match position.
            size_t earliest_stop = std::string::npos;
            for (const auto& stop : stops) {
                const size_t pos = pending.find(stop);
                if (pos != std::string::npos && (earliest_stop == std::string::npos || pos < earliest_stop)) {
                    earliest_stop = pos;
                }
            }
            if (earliest_stop != std::string::npos) {
                const size_t safe_len = earliest_stop - trailing_incomplete_utf8_bytes(pending, earliest_stop);
                if (safe_len > 0) {
                    emit_bytes(env, listener, emit, pending.data(), safe_len);
                }
                pending.clear();
                hit_stop = true;
                break;
            }

            // Compute longest pending suffix matching any stop-sequence prefix.
            size_t stop_holdback = 0;
            for (const auto& stop : stops) {
                const size_t max_prefix = std::min(pending.size(), stop.size() > 0 ? stop.size() - 1 : 0);
                for (size_t len = max_prefix; len > stop_holdback; --len) {
                    if (pending.compare(pending.size() - len, len, stop, 0, len) == 0) {
                        stop_holdback = len;
                        break;
                    }
                }
            }

            size_t safe_len = pending.size() > stop_holdback ? (pending.size() - stop_holdback) : 0;
            if (safe_len > 0) {
                safe_len -= trailing_incomplete_utf8_bytes(pending, safe_len);
            }
            if (safe_len > 0) {
                if (!emit_bytes(env, listener, emit, pending.data(), safe_len)) {
                    pending.clear();
                    break;
                }
                pending.erase(0, safe_len);
            }
        }

        history_tokens.push_back(token);
        const int h_size = static_cast<int>(history_tokens.size());
        if (h_size >= 5 &&
            history_tokens[h_size - 1] == history_tokens[h_size - 2] &&
            history_tokens[h_size - 2] == history_tokens[h_size - 3] &&
            history_tokens[h_size - 3] == history_tokens[h_size - 4] &&
            history_tokens[h_size - 4] == history_tokens[h_size - 5]) {
            break;
        }
        if (h_size >= 32) {
            bool is_loop = true;
            for (int k = 0; k < 8; ++k) {
                if (history_tokens[h_size - 1 - k] != history_tokens[h_size - 9 - k] ||
                    history_tokens[h_size - 9 - k] != history_tokens[h_size - 17 - k]) {
                    is_loop = false;
                    break;
                }
            }
            if (is_loop) break;
        }

        llama_batch one = llama_batch_get_one(const_cast<llama_token*>(&token), 1);
        const int decode_rc = llama_decode(session->context, one);
        if (decode_rc != 0) {
            llama_kv_cache_clear(session->context);
            session->cached_tokens.clear();
            if (session->abort_requested.load(std::memory_order_relaxed)) {
                break;
            }
            llama_sampler_free(sampler);
            throw_java(env, "GGUF decoding failed");
            return -1;
        }
        session->cached_tokens.push_back(token);

        std::this_thread::yield();
    }

    if (!hit_stop && !pending.empty() && !session->abort_requested.load(std::memory_order_relaxed)) {
        const size_t safe_len = pending.size() - trailing_incomplete_utf8_bytes(pending, pending.size());
        if (safe_len > 0) {
            emit_bytes(env, listener, emit, pending.data(), safe_len);
        }
    }

    llama_sampler_free(sampler);
    return generated;
}

}  // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("com/example/on_device_qwen/inference/DolphinNativeEngine");
    if (!cls) return JNI_ERR;
    static JNINativeMethod methods[] = {
        {"nativeOpen", "(Ljava/lang/String;II)J", reinterpret_cast<void*>(native_open)},
        {"nativeGenerate", "(JLjava/lang/String;IFFIFFI[Ljava/lang/String;Lcom/example/on_device_qwen/inference/ChunkListener;)I", reinterpret_cast<void*>(native_generate)},
        {"nativeClose", "(J)V", reinterpret_cast<void*>(native_close)},
        {"nativeRequestAbort", "(J)V", reinterpret_cast<void*>(native_request_abort)},
        {"nativeResetContext", "(J)Z", reinterpret_cast<void*>(native_reset_context)},
        {"nativeCountTokens", "(JLjava/lang/String;)I", reinterpret_cast<void*>(native_count_tokens)},
        {"nativeApplyChatTemplate", "(J[Ljava/lang/String;[Ljava/lang/String;Z)Ljava/lang/String;", reinterpret_cast<void*>(native_apply_chat_template)},
    };
    const int count = static_cast<int>(sizeof(methods) / sizeof(methods[0]));
    const int result = env->RegisterNatives(cls, methods, count);
    env->DeleteLocalRef(cls);
    return result == 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
