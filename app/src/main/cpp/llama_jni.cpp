// The only bridge between Sadu and llama.cpp. Deliberately small: load a model, run a prompt,
// stream the pieces back to Kotlin, stop on request. Everything else (history, prompt building,
// settings) stays in Kotlin where it is testable.
#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "SaduLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Last failure, read by LlamaEngine after a null/empty return. Per thread, because generation
// already runs off the main thread.
thread_local std::string g_error;

void setError(const std::string &message) {
    g_error = message;
    LOGE("%s", message.c_str());
}

void clearError() {
    g_error.clear();
}

std::string fromJava(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring toJava(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}

void ensureBackend() {
    static std::once_flag once;
    std::call_once(once, [] { llama_backend_init(); });
}

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<bool> loading{false};
    std::mutex busy;
};

Session *sessionOf(jlong handle) {
    return reinterpret_cast<Session *>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeLoad(
        JNIEnv *env, jobject, jstring path, jint nCtx, jint nThreads, jint nBatch) {
    clearError();
    ensureBackend();

    const std::string file = fromJava(env, path);
    if (file.empty()) {
        setError("No model file was given.");
        return 0;
    }

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;  // CPU only for now: the first honest step.
    modelParams.vocab_only = false;
    modelParams.check_tensors = false;

    llama_model *model = llama_model_load_from_file(file.c_str(), modelParams);
    if (model == nullptr) {
        setError("llama.cpp could not read that file. It has to be a .gguf model this phone can run.");
        return 0;
    }

    auto *session = new Session();
    session->model = model;

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx > 0 ? nCtx : 4096);
    ctxParams.n_batch = static_cast<uint32_t>(nBatch > 0 ? nBatch : 512);
    ctxParams.n_ubatch = ctxParams.n_batch;
    ctxParams.n_threads = nThreads > 0 ? nThreads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;
    ctxParams.no_perf = true;
    // Lets a stop request interrupt a decode that is already running.
    ctxParams.abort_callback = [](void *data) -> bool {
        return static_cast<Session *>(data)->cancelled.load();
    };
    ctxParams.abort_callback_data = session;

    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        llama_model_free(model);
        delete session;
        setError("The model loaded but the context could not be created - most likely not enough free memory.");
        return 0;
    }

    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);
    LOGI("loaded %s (ctx=%u batch=%u threads=%d)", file.c_str(), llama_n_ctx(ctx), llama_n_batch(ctx),
         ctxParams.n_threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeFree(JNIEnv *, jobject, jlong handle) {
    Session *session = sessionOf(handle);
    if (session == nullptr) return;
    session->cancelled.store(true);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

JNIEXPORT void JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeCancel(JNIEnv *, jobject, jlong handle) {
    Session *session = sessionOf(handle);
    if (session != nullptr) session->cancelled.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeLastError(JNIEnv *env, jobject) {
    return toJava(env, g_error);
}

// "description;parameters;sizeInBytes;trainedContext;hasChatTemplate" - one string so the UI can
// show what is actually loaded without a second round trip.
JNIEXPORT jstring JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeModelInfo(JNIEnv *env, jobject, jlong handle) {
    Session *session = sessionOf(handle);
    if (session == nullptr) return toJava(env, "");

    char description[512] = {0};
    if (llama_model_desc(session->model, description, sizeof(description)) < 0) {
        snprintf(description, sizeof(description), "gguf model");
    }
    const bool hasTemplate = llama_model_chat_template(session->model, nullptr) != nullptr;

    char out[768];
    snprintf(out, sizeof(out), "%s;%llu;%llu;%d;%d", description,
             static_cast<unsigned long long>(llama_model_n_params(session->model)),
             static_cast<unsigned long long>(llama_model_size(session->model)),
             llama_model_n_ctx_train(session->model), hasTemplate ? 1 : 0);
    return toJava(env, out);
}

// The model's own template, applied by llama.cpp over a whole conversation. An empty string means
// the model carries no usable template and Kotlin should fall back to the frozen Gemma one.
JNIEXPORT jstring JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeApplyTemplate(
        JNIEnv *env, jobject, jlong handle, jobjectArray roles, jobjectArray contents) {
    Session *session = sessionOf(handle);
    if (session == nullptr) return toJava(env, "");

    const char *tmpl = llama_model_chat_template(session->model, nullptr);
    if (tmpl == nullptr) return toJava(env, "");

    const jsize count = roles != nullptr ? env->GetArrayLength(roles) : 0;
    if (count <= 0 || contents == nullptr || env->GetArrayLength(contents) != count) {
        return toJava(env, "");
    }

    // Both the roles and the contents have to outlive the call, so they are kept here.
    std::vector<std::string> roleTexts;
    std::vector<std::string> contentTexts;
    std::vector<llama_chat_message> messages;
    roleTexts.reserve(static_cast<size_t>(count));
    contentTexts.reserve(static_cast<size_t>(count));
    messages.reserve(static_cast<size_t>(count));

    for (jsize i = 0; i < count; ++i) {
        auto *role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto *content = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        roleTexts.push_back(fromJava(env, role));
        contentTexts.push_back(fromJava(env, content));
        if (role != nullptr) env->DeleteLocalRef(role);
        if (content != nullptr) env->DeleteLocalRef(content);
    }
    for (jsize i = 0; i < count; ++i) {
        messages.push_back({roleTexts[static_cast<size_t>(i)].c_str(), contentTexts[static_cast<size_t>(i)].c_str()});
    }

    // add_assistant: the prompt ends where the model starts writing.
    int needed = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
    if (needed <= 0) return toJava(env, "");

    std::vector<char> buffer(static_cast<size_t>(needed) + 1);
    int written = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buffer.data(),
                                            static_cast<int32_t>(buffer.size()));
    if (written <= 0) return toJava(env, "");
    return toJava(env, std::string(buffer.data(), static_cast<size_t>(written)));
}

JNIEXPORT jstring JNICALL
Java_com_sadu_assistant_brain_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject, jlong handle, jstring prompt, jfloat temperature, jint topK, jfloat topP,
        jfloat minP, jfloat repeatPenalty, jint repeatLastN, jint maxTokens, jobject sink) {
    clearError();

    Session *session = sessionOf(handle);
    if (session == nullptr) {
        setError("No gguf model is loaded.");
        return toJava(env, "");
    }

    std::lock_guard<std::mutex> guard(session->busy);
    session->cancelled.store(false);

    const std::string text = fromJava(env, prompt);
    if (text.empty()) {
        setError("The prompt was empty.");
        return toJava(env, "");
    }

    const int contextSize = static_cast<int>(llama_n_ctx(session->ctx));
    const int limit = maxTokens > 0 ? maxTokens : 512;

    int tokenCount = -llama_tokenize(session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                     nullptr, 0, true, true);
    if (tokenCount <= 0) {
        setError("The prompt could not be turned into tokens.");
        return toJava(env, "");
    }
    std::vector<llama_token> tokens(static_cast<size_t>(tokenCount));
    tokenCount = llama_tokenize(session->vocab, text.c_str(), static_cast<int32_t>(text.size()),
                                tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (tokenCount <= 0) {
        setError("The prompt could not be turned into tokens.");
        return toJava(env, "");
    }
    tokens.resize(static_cast<size_t>(tokenCount));
    if (static_cast<int>(tokens.size()) >= contextSize - 4) {
        setError("This conversation no longer fits the model's context window. Start a new chat, or "
                 "make the context window smaller in Settings.");
        return toJava(env, "");
    }

    // Every turn re-sends the whole prompt, so the old cache would only get in the way.
    llama_memory_clear(llama_get_memory(session->ctx), true);

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    samplerParams.no_perf = true;
    llama_sampler *chain = llama_sampler_chain_init(samplerParams);
    if (repeatPenalty > 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(
                llama_vocab_n_tokens(session->vocab), repeatLastN, repeatPenalty, 0.0f, 0.0f));
    }
    if (topK > 0) llama_sampler_chain_add(chain, llama_sampler_init_top_k(topK));
    if (topP > 0.0f) llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
    if (minP > 0.0f) llama_sampler_chain_add(chain, llama_sampler_init_min_p(minP, 1));
    if (temperature > 0.0f) llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jmethodID onToken = nullptr;
    if (sink != nullptr) {
        jclass sinkClass = env->GetObjectClass(sink);
        if (sinkClass != nullptr) {
            onToken = env->GetMethodID(sinkClass, "onToken", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(sinkClass);
        }
    }

    std::string answer;
    bool failed = false;
    const int batchSize = static_cast<int>(llama_n_batch(session->ctx));

    for (int at = 0; at < static_cast<int>(tokens.size());) {
        const int chunk = std::min(batchSize, static_cast<int>(tokens.size()) - at);
        const int rc = llama_decode(session->ctx, llama_batch_get_one(tokens.data() + at, chunk));
        if (rc != 0) {
            if (rc != 2) {
                setError("The model could not read the prompt (code " + std::to_string(rc) + ").");
            }
            failed = true;
            break;
        }
        at += chunk;
    }

    // Declared outside the loop on purpose: the batch keeps a pointer to it.
    llama_token next = 0;
    for (int made = 0; !failed && made < limit; ++made) {
        if (session->cancelled.load()) break;
        next = llama_sampler_sample(chain, session->ctx, -1);
        if (llama_vocab_is_eog(session->vocab, next)) break;

        char piece[256];
        const int pieceLength = llama_token_to_piece(session->vocab, next, piece, sizeof(piece), 0, false);
        if (pieceLength > 0) {
            answer.append(piece, static_cast<size_t>(pieceLength));
            if (onToken != nullptr) {
                const std::string chunk(piece, static_cast<size_t>(pieceLength));
                jstring javaPiece = env->NewStringUTF(chunk.c_str());
                if (javaPiece != nullptr) {
                    env->CallVoidMethod(sink, onToken, javaPiece);
                    env->DeleteLocalRef(javaPiece);
                }
                // A throw in the callback (a cancelled screen, say) must not kill the build-up of
                // the answer: stop streaming and keep generating.
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    onToken = nullptr;
                }
            }
        }
        // llama_sampler_sample already accepted the token into the chain's history.
        const int rc = llama_decode(session->ctx, llama_batch_get_one(&next, 1));
        if (rc != 0) {
            if (rc != 2) {
                setError("The model stopped early (code " + std::to_string(rc) + ").");
            }
            break;
        }
    }

    llama_sampler_free(chain);
    LOGI("generated %zu chars", answer.size());
    return toJava(env, answer);
}

}  // extern "C"
