package com.college.placement.attendance.crypto

import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Android Keystore implementation of CryptoManager.
 * Stores NIST P-256 (secp256r1) private keys inside hardware-backed TEE / StrongBox.
 * Requires biometric unlock for every signature generation.
 */
class AndroidKeystoreCryptoManager : CryptoManager {

    override fun generateKeyPair(): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Generate NIST P-256 KeyPair in Android KeyStore
        val kpg = KeyPairGenerator.getInstance(
            "EC",
            ANDROID_KEYSTORE
        )

        try {
            // Using reflection or standard Android KeyGenParameterSpec
            val specClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val keyPropertiesClass = Class.forName("android.security.keystore.KeyProperties")
            val purposeSign = keyPropertiesClass.getField("PURPOSE_SIGN").getInt(null)
            val digestSha256 = keyPropertiesClass.getField("DIGEST_SHA256").get(null) as String

            val builderConstructor = specClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
            val builder = builderConstructor.newInstance(KEY_ALIAS, purposeSign)

            // setDigests(KeyProperties.DIGEST_SHA256)
            val setDigestsMethod = specClass.getMethod("setDigests", Array<String>::class.java)
            setDigestsMethod.invoke(builder, arrayOf(digestSha256))

            // setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            val setAlgoParamMethod = specClass.getMethod("setAlgorithmParameterSpec", java.security.spec.AlgorithmParameterSpec::class.java)
            setAlgoParamMethod.invoke(builder, ECGenParameterSpec("secp256r1"))

            // setUserAuthenticationRequired(true)
            val setUserAuthMethod = specClass.getMethod("setUserAuthenticationRequired", Boolean::class.javaPrimitiveType)
            setUserAuthMethod.invoke(builder, true)

            // build()
            val buildMethod = specClass.getMethod("build")
            val spec = buildMethod.invoke(builder) as java.security.spec.AlgorithmParameterSpec

            kpg.initialize(spec)
        } catch (e: ClassNotFoundException) {
            // Fallback for JVM testing / non-Android environments
            val ecSpec = ECGenParameterSpec("secp256r1")
            kpg.initialize(ecSpec)
        }

        val kp = kpg.generateKeyPair()
        return formatPem(kp.public.encoded)
    }

    override fun getPublicKeyPem(): String? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
        return formatPem(cert.publicKey.encoded)
    }

    override fun getKeyFingerprint(): String? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(cert.publicKey.encoded)
        return hash.joinToString("") { "%02X".format(it) }
    }

    override fun isKeyEnrolled(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.containsAlias(KEY_ALIAS)
    }

    override fun initSignature(): Signature {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Key alias '$KEY_ALIAS' not found in AndroidKeyStore")

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
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
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "placement_attendance_key"

        fun formatPem(derBytes: ByteArray): String {
            val base64 = Base64.getEncoder().encodeToString(derBytes)
            val chunks = base64.chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$chunks\n-----END PUBLIC KEY-----"
        }
    }
}
