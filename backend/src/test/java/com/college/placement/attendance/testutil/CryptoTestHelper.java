package com.college.placement.attendance.testutil;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public class CryptoTestHelper {

    public static KeyPair generateP256KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec, new SecureRandom());
        return kpg.generateKeyPair();
    }

    public static String toPem(PublicKey publicKey) {
        String base64Key = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Key +
                "\n-----END PUBLIC KEY-----";
    }

    public static String signCanonicalMessage(PrivateKey privateKey, String canonicalMessage) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(canonicalMessage.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signature.sign();
        return Base64.getEncoder().encodeToString(sigBytes);
    }
}
