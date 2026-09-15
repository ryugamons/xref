#include "xt_utils.h"
#include "xt_parts.h"

#include <openssl/pem.h>
#include <openssl/evp.h>
#include <openssl/rsa.h>
#include <openssl/sha.h>
#include <openssl/rand.h>
#include <openssl/bio.h>
#include <openssl/crypto.h>

#include <cstring>

void secureZero(void* ptr, size_t len) {
    OPENSSL_cleanse(ptr, len);
}

static std::vector<uint8_t> base64Decode(const std::string& in) {
    BIO* bio = BIO_new_mem_buf(in.data(), static_cast<int>(in.size()));
    BIO* b64 = BIO_new(BIO_f_base64());
    BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
    bio = BIO_push(b64, bio);

    std::vector<uint8_t> out(in.size());
    int len = BIO_read(bio, out.data(), static_cast<int>(in.size()));
    BIO_free_all(bio);

    if (len <= 0) return {};
    out.resize(static_cast<size_t>(len));
    return out;
}

bool verifySignature(const std::string& challenge, const std::string& signatureB64) {
    std::string pem = getObfuscatedPublicKeyPem();

    BIO* bio = BIO_new_mem_buf(pem.data(), static_cast<int>(pem.size()));
    EVP_PKEY* pkey = PEM_read_bio_PUBKEY(bio, nullptr, nullptr, nullptr);
    BIO_free(bio);

    secureZero(pem.data(), pem.size());

    if (!pkey) return false;

    std::vector<uint8_t> sig = base64Decode(signatureB64);
    if (sig.empty()) {
        EVP_PKEY_free(pkey);
        return false;
    }

    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    bool ok = false;

    if (ctx &&
        EVP_DigestVerifyInit(ctx, nullptr, EVP_sha256(), nullptr, pkey) == 1 &&
        EVP_DigestVerifyUpdate(ctx, challenge.data(), challenge.size()) == 1) {
        int rc = EVP_DigestVerifyFinal(ctx, sig.data(), sig.size());
        ok = (rc == 1);
    }

    if (ctx) EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(pkey);
    return ok;
}

std::vector<uint8_t> deriveSessionKey(const std::string& challenge,
                                      const std::string& signatureB64) {
    std::string material = challenge + signatureB64;
    std::vector<uint8_t> digest(SHA256_DIGEST_LENGTH);
    SHA256(reinterpret_cast<const unsigned char*>(material.data()),
           material.size(), digest.data());
    secureZero(material.data(), material.size());
    return digest;
}

std::vector<uint8_t> randomBytes(size_t len) {
    std::vector<uint8_t> buf(len);
    RAND_bytes(buf.data(), static_cast<int>(len));
    return buf;
}

std::vector<uint8_t> aesGcmDecrypt(const std::vector<uint8_t>& key,
                                   const std::vector<uint8_t>& input) {
    if (input.size() < 12 + 16 || key.size() != 32) return {};

    const uint8_t* nonce = input.data();
    const uint8_t* ciphertext = input.data() + 12;
    size_t ciphertextLen = input.size() - 12 - 16;
    const uint8_t* tag = input.data() + input.size() - 16;

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return {};

    std::vector<uint8_t> plaintext(ciphertextLen);
    int outLen = 0, totalLen = 0;
    bool ok = true;

    ok = ok && EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1;
    ok = ok && EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, 12, nullptr) == 1;
    ok = ok && EVP_DecryptInit_ex(ctx, nullptr, nullptr, key.data(), nonce) == 1;
    ok = ok && EVP_DecryptUpdate(ctx, plaintext.data(), &outLen,
                                 ciphertext, static_cast<int>(ciphertextLen)) == 1;
    totalLen = outLen;
    ok = ok && EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16,
                                   const_cast<uint8_t*>(tag)) == 1;
    ok = ok && EVP_DecryptFinal_ex(ctx, plaintext.data() + totalLen, &outLen) == 1;
    totalLen += outLen;

    EVP_CIPHER_CTX_free(ctx);

    if (!ok) {
        secureZero(plaintext.data(), plaintext.size());
        return {};
    }

    plaintext.resize(static_cast<size_t>(totalLen));
    return plaintext;
}
