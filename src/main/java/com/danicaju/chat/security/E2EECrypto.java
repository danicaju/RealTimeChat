package com.danicaju.chat.security;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utilidad de cifrado de extremo a extremo (E2EE) para Mensajes Directos 1 a 1.
 * Implementa intercambio de claves ECDH (curva secp256r1) y cifrado autenticado AES-256-GCM.
 */
public final class E2EECrypto {

    private static final String EC_ALGORITHM = "EC";
    private static final String EC_CURVE = "secp256r1";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private E2EECrypto() {
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(EC_ALGORITHM);
        kpg.initialize(new ECGenParameterSpec(EC_CURVE), RANDOM);
        return kpg.generateKeyPair();
    }

    public static String encodePublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64);
        KeyFactory kf = KeyFactory.getInstance(EC_ALGORITHM);
        return kf.generatePublic(new X509EncodedKeySpec(encoded));
    }

    /**
     * Deriva la clave simétrica AES-256 compartida mediante ECDH + SHA-256.
     */
    public static SecretKey deriveSharedKey(PrivateKey myPrivateKey, PublicKey peerPublicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
        agreement.init(myPrivateKey);
        agreement.doPhase(peerPublicKey, true);
        byte[] rawSecret = agreement.generateSecret();

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] derived = sha256.digest(rawSecret);
        return new SecretKeySpec(derived, "AES");
    }

    public static EncryptedPayload encrypt(String plaintext, SecretKey sharedKey) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_BYTES];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return new EncryptedPayload(
                Base64.getEncoder().encodeToString(cipherBytes),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    public static String decrypt(String ciphertextBase64, String ivBase64, SecretKey sharedKey) throws GeneralSecurityException {
        byte[] cipherBytes = Base64.getDecoder().decode(ciphertextBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    public static String fingerprint(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
            return "N/A";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(publicKeyBase64);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(decoded);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && i < digest.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "INVALID";
        }
    }

    public record EncryptedPayload(String ciphertextBase64, String ivBase64) {
    }
}
