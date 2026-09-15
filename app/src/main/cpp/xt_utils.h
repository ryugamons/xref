#pragma once
#include <string>
#include <vector>
#include <cstdint>

/**
 * Securely wipes memory.
 */
void secureZero(void* ptr, size_t len);

/**
 * Verifies that 'signatureB64' is a valid RSA-SHA256 signature of 'challenge'
 * using the embedded public key.
 */
bool verifySignature(const std::string& challenge, const std::string& signatureB64);

/**
 * Derives a 32-byte session key from the challenge and signature.
 */
std::vector<uint8_t> deriveSessionKey(const std::string& challenge,
                                      const std::string& signatureB64);

/**
 * Generates 'len' cryptographically secure random bytes.
 */
std::vector<uint8_t> randomBytes(size_t len);

/**
 * Decrypts a payload using AES-256-GCM.
 * Input format: [12-byte nonce][ciphertext...][16-byte tag]
 */
std::vector<uint8_t> aesGcmDecrypt(const std::vector<uint8_t>& key,
                                   const std::vector<uint8_t>& input);
