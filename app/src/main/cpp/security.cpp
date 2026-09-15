#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <unistd.h>
#include <signal.h>
#include <cstdlib>
#include <ctime>
#include "xt_utils.h"
#include "frida_detect.h"

namespace {
    std::mutex g_keyMutex;
    std::vector<uint8_t> g_sessionKey;
    bool g_seeded = false;
}

static void seedRngOnce() {
    if (!g_seeded) {
        srand(static_cast<unsigned int>(time(nullptr)) ^ getpid());
        g_seeded = true;
    }
}

static std::vector<uint8_t> jbyteArrayToVector(JNIEnv* env, jbyteArray arr) {
    if (!arr) return {};
    jsize len = env->GetArrayLength(arr);
    std::vector<uint8_t> out(static_cast<size_t>(len));
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

static jbyteArray vectorToJbyteArray(JNIEnv* env, const std::vector<uint8_t>& v) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(v.size()));
    if (!v.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(v.size()),
                                reinterpret_cast<const jbyte*>(v.data()));
    }
    return arr;
}

extern "C" {

JNIEXPORT void JNICALL
Java_id_xterm_core_security_SecurityManager_nativeInit(JNIEnv* env, jobject /* thiz */) {
seedRngOnce();
std::lock_guard<std::mutex> lock(g_keyMutex);
g_sessionKey = randomBytes(32);
}

JNIEXPORT void JNICALL
Java_id_xterm_core_security_SecurityManager_nativeVerifyAndDeriveKey(
        JNIEnv* env, jobject /* thiz */,
jstring jUsername, jstring jChallenge, jstring jSignatureB64) {

const char* challengeC = env->GetStringUTFChars(jChallenge, nullptr);
const char* sigC = env->GetStringUTFChars(jSignatureB64, nullptr);

std::string challenge(challengeC);
std::string signatureB64(sigC);

env->ReleaseStringUTFChars(jChallenge, challengeC);
env->ReleaseStringUTFChars(jSignatureB64, sigC);

bool valid = false;
std::vector<uint8_t> newKey;

try {
valid = verifySignature(challenge, signatureB64);
newKey = valid ? deriveSessionKey(challenge, signatureB64)
               : randomBytes(32);
} catch (...) {
newKey = randomBytes(32);
}

{
std::lock_guard<std::mutex> lock(g_keyMutex);
g_sessionKey = newKey;
}

secureZero(challenge.data(), challenge.size());
secureZero(signatureB64.data(), signatureB64.size());
}

JNIEXPORT jboolean JNICALL
        Java_id_xterm_core_security_SecurityManager_nativeTryDecryptCanary(
        JNIEnv* env, jobject /* thiz */, jbyteArray jCanary) {

std::vector<uint8_t> canary = jbyteArrayToVector(env, jCanary);
std::vector<uint8_t> key;
{
std::lock_guard<std::mutex> lock(g_keyMutex);
key = g_sessionKey;
}

std::vector<uint8_t> plaintext = aesGcmDecrypt(key, canary);
secureZero(key.data(), key.size());

bool ok = !plaintext.empty();
secureZero(plaintext.data(), plaintext.size());
return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
        Java_id_xterm_core_security_SecurityManager_nativeDecryptPayload(
        JNIEnv* env, jobject /* thiz */, jbyteArray jEncrypted) {

std::vector<uint8_t> encrypted = jbyteArrayToVector(env, jEncrypted);
std::vector<uint8_t> key;
{
std::lock_guard<std::mutex> lock(g_keyMutex);
key = g_sessionKey;
}

std::vector<uint8_t> plaintext = aesGcmDecrypt(key, encrypted);
secureZero(key.data(), key.size());

if (plaintext.empty()) return nullptr;

jbyteArray result = vectorToJbyteArray(env, plaintext);
secureZero(plaintext.data(), plaintext.size());
return result;
}


JNIEXPORT jboolean JNICALL
Java_id_xterm_core_security_SecurityManager_nativeCheckIntegrity(
        JNIEnv* env, jobject /* thiz */) {

    bool suspicious = detectFridaInstrumentation();

    if (suspicious) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_id_xterm_core_security_SecurityManager_nativeKill(JNIEnv* env, jobject /* thiz */) {
seedRngOnce();
useconds_t delay = static_cast<useconds_t>(rand() % 3000000 + 300000);
usleep(delay);

{
std::lock_guard<std::mutex> lock(g_keyMutex);
if (!g_sessionKey.empty()) {
secureZero(g_sessionKey.data(), g_sessionKey.size());
}
}

kill(getpid(), SIGKILL);
abort();
}

}
