package com.college.placement.attendance.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class CryptoManagerTest {

    private lateinit var cryptoManager: StandardJvmCryptoManager

    @BeforeEach
    fun setUp() {
        cryptoManager = StandardJvmCryptoManager()
    }

    @Test
    @DisplayName("generateKeyPair produces valid NIST P-256 X.509 PEM and 64-char hex fingerprint")
    fun testGenerateKeyPair() {
        val pem = cryptoManager.generateKeyPair()

        assertThat(pem).isNotNull()
        assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----\n")
        assertThat(pem).endsWith("\n-----END PUBLIC KEY-----")

        val fingerprint = cryptoManager.getKeyFingerprint()
        assertThat(fingerprint).isNotNull()
        assertThat(fingerprint).hasSize(64) // SHA-256 hex string length
        assertThat(fingerprint).matches("^[0-9A-F]{64}$")
    }

    @Test
    @DisplayName("Sign challenge with ECDSA P-256 and verify with public key")
    fun testSignAndVerifyChallenge() {
        val pem = cryptoManager.generateKeyPair()

        // 32-byte simulated server nonce challenge
        val challengeBytes = ByteArray(32) { (it * 7).toByte() }

        val signatureBytes = cryptoManager.signChallengeDirect(challengeBytes)
        assertThat(signatureBytes).isNotNull()
        assertThat(signatureBytes.size).isBetween(64, 73) // DER-encoded ECDSA signature

        // Decode public key from PEM
        val cleanBase64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val derBytes = Base64.getDecoder().decode(cleanBase64)

        val keyFactory = KeyFactory.getInstance("EC")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(derBytes))

        // Verify signature with public key
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(challengeBytes)
        val isValid = verifier.verify(signatureBytes)

        assertThat(isValid).isTrue()

        // Verify tampering with challenge fails
        val tamperedBytes = challengeBytes.copyOf()
        tamperedBytes[0] = (tamperedBytes[0] + 1).toByte()

        val tamperedVerifier = Signature.getInstance("SHA256withECDSA")
        tamperedVerifier.initVerify(publicKey)
        tamperedVerifier.update(tamperedBytes)
        val isTamperedValid = tamperedVerifier.verify(signatureBytes)

        assertThat(isTamperedValid).isFalse()
    }
}
