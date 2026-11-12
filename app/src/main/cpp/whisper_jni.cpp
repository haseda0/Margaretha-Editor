// JNI bridge: Kotlin passes 16 kHz mono float PCM, gets back "t0\tt1\ttext\n" lines (centiseconds, UTF-8 bytes).
#include <jni.h>
#include <atomic>
#include <string>
#include "whisper.h"

static std::atomic<bool> g_abort{false};

struct Progress { JNIEnv* env; jobject cb; jmethodID m; };

extern "C" JNIEXPORT jlong JNICALL
Java_id_haseda_margaretha_media_WhisperNative_nativeInit(JNIEnv* env, jclass, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_id_haseda_margaretha_media_WhisperNative_nativeFree(JNIEnv*, jclass, jlong ctx) {
    if (ctx) whisper_free(reinterpret_cast<whisper_context*>(ctx));
}

extern "C" JNIEXPORT void JNICALL
Java_id_haseda_margaretha_media_WhisperNative_nativeAbort(JNIEnv*, jclass) { g_abort = true; }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_id_haseda_margaretha_media_WhisperNative_nativeTranscribe(JNIEnv* env, jclass, jlong handle, jfloatArray pcm,
                                                                jstring lang, jint threads, jobject callback) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (!ctx) return nullptr;
    g_abort = false;
    const char* language = env->GetStringUTFChars(lang, nullptr);
    std::string langCopy(language);
    env->ReleaseStringUTFChars(lang, language);

    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads;
    wp.language = langCopy.c_str();
    wp.translate = false;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.token_timestamps = true;
    wp.max_len = 42;          // short, readable subtitle lines
    wp.split_on_word = true;
    wp.suppress_blank = true;

    Progress pr{env, callback, nullptr};
    if (callback) {
        jclass cls = env->GetObjectClass(callback);
        pr.m = env->GetMethodID(cls, "onProgress", "(I)V");
        wp.progress_callback = [](whisper_context*, whisper_state*, int progress, void* user) {
            auto* p = static_cast<Progress*>(user);
            if (p->m) p->env->CallVoidMethod(p->cb, p->m, progress);
        };
        wp.progress_callback_user_data = &pr;
    }
    wp.abort_callback = [](void*) { return g_abort.load(); };

    jsize n = env->GetArrayLength(pcm);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, wp, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0 || g_abort) return nullptr;

    std::string out;
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        out += std::to_string(whisper_full_get_segment_t0(ctx, i));
        out += '\t';
        out += std::to_string(whisper_full_get_segment_t1(ctx, i));
        out += '\t';
        std::string t = whisper_full_get_segment_text(ctx, i);
        for (char& c : t) if (c == '\n' || c == '\t') c = ' ';
        out += t;
        out += '\n';
    }
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte*>(out.data()));
    return arr;
}
