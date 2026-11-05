package com.college.placement.attendance.service.crypto;

import com.college.placement.attendance.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
public class CryptoVerificationService {

    private static final String EC_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /**
     * Parses an X.509 EC public key from PEM string.
     */
    public PublicKey parsePublicKeyFromPem(String pem) {
        try {
            String sanitized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(sanitized);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decoded));

            if (!(publicKey instanceof ECPublicKey)) {
                throw new BadRequestException("Supplied public key is not an Elliptic Curve (EC) key.");
            }

            return publicKey;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse EC public key from PEM: {}", e.getMessage());
            throw new BadRequestException("Invalid public key PEM format: " + e.getMessage());
        }
    }

    /**
     * Computes the SHA-256 fingerprint (hex format) of an X.509 public key.
     */
    public String computePublicKeyFingerprint(PublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest algorithm not available", e);
        }
    }

    /**
     * Verifies an ECDSA signature over canonical message bytes against an EC public key.
     */
    public boolean verifySignature(PublicKey publicKey, String canonicalMessage, String base64Signature) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            byte[] messageBytes = canonicalMessage.getBytes(StandardCharsets.UTF_8);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(messageBytes);

            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("ECDSA signature verification failed with error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Builds canonical message string for attendance signing.
     */
    public String buildCanonicalMessage(String challengeToken, Long studentId, Long driveId, Long deviceId, Long stationId, long timestampEpochSec) {
        return challengeToken + "|" + studentId + "|" + driveId + "|" + deviceId + "|" + stationId + "|" + timestampEpochSec;
    }
}
