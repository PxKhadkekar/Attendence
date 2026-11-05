package com.college.placement.attendance.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Standard Java Cryptography Architecture (JCA) implementation of CryptoManager.
 * Uses standard NIST P-256 (secp256r1) keypair generation and SHA256withECDSA signing.
 * Compatible with unit tests, CI runners, and backend validation.
 */
class StandardJvmCryptoManager : CryptoManager {

    private var keyPair: KeyPair? = null

    override fun generateKeyPair(): String {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(ecSpec)
        val generated = keyPairGenerator.generateKeyPair()
        this.keyPair = generated
        return formatPem(generated.public.encoded)
    }

    override fun getPublicKeyPem(): String? {
        val kp = keyPair ?: return null
        return formatPem(kp.public.encoded)
    }

    override fun getKeyFingerprint(): String? {
        val kp = keyPair ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(kp.public.encoded)
        return hash.joinToString("") { "%02X".format(it) }
    }

    override fun isKeyEnrolled(): Boolean = keyPair != null

    override fun initSignature(): Signature {
        val kp = keyPair ?: throw IllegalStateException("No keypair enrolled. Call generateKeyPair() first.")
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(kp.private)
        return signature
    }

    override fun signChallenge(signature: Signature, challengeBytes: ByteArray): ByteArray {
        signature.update(challengeBytes)
        return signature.sign()
    }

    override fun signChallengeDirect(challengeBytes: ByteArray): ByteArray {
        val signature = initSignature()
        return signChallenge(signature, challengeBytes)
    }

    override fun deleteKey() {
        keyPair = null
    }

    companion object {
        fun formatPem(derBytes: ByteArray): String {
            val base64 = Base64.getEncoder().encodeToString(derBytes)
            val chunks = base64.chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$chunks\n-----END PUBLIC KEY-----"
        }
    }
}
