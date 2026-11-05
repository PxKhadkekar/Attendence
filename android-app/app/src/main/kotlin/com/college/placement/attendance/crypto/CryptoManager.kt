package com.college.placement.attendance.crypto

import java.security.Signature

/**
 * Interface defining hardware-backed cryptographic operations for zero-trust attendance.
 * All keys use NIST P-256 (secp256r1) with SHA256withECDSA.
 */
interface CryptoManager {
    /**
     * Generates a new NIST P-256 keypair.
     * In AndroidKeystore, this is backed by hardware TEE/StrongBox with biometric unlock requirement.
     * Returns the SubjectPublicKeyInfo in X.509 PEM format.
     */
    fun generateKeyPair(): String

    /**
     * Returns the enrolled public key in PEM format, or null if no key is generated yet.
     */
    fun getPublicKeyPem(): String?

    /**
     * Returns the SHA-256 hex fingerprint of the public key's DER encoding.
     */
    fun getKeyFingerprint(): String?

    /**
     * Checks if a cryptographic key is already enrolled.
     */
    fun isKeyEnrolled(): Boolean

    /**
     * Prepares and initializes a Signature object with the Keystore private key.
     * Passed into AndroidX BiometricPrompt.CryptoObject.
     */
    fun initSignature(): Signature

    /**
     * Signs challenge nonce data using an already unlocked/authenticated Signature object.
     */
    fun signChallenge(signature: Signature, challengeBytes: ByteArray): ByteArray

    /**
     * Signs challenge nonce directly (used in tests or automated flows).
     */
    fun signChallengeDirect(challengeBytes: ByteArray): ByteArray

    /**
     * Removes the enrolled key from storage.
     */
    fun deleteKey()
}
