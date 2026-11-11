package com.danicaju.chat.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Cifrado E2EE (ECDH + AES-GCM)")
class E2EECryptoTest {

    @Test
    @DisplayName("Intercambio ECDH simétrico y cifrado/descifrado AES-256-GCM")
    void testEcdhKeyExchangeAndAesGcm() throws Exception {
        KeyPair aliceKeys = E2EECrypto.generateKeyPair();
        KeyPair bobKeys = E2EECrypto.generateKeyPair();

        String alicePubEncoded = E2EECrypto.encodePublicKey(aliceKeys.getPublic());
        String bobPubEncoded = E2EECrypto.encodePublicKey(bobKeys.getPublic());

        PublicKey aliceDecoded = E2EECrypto.decodePublicKey(alicePubEncoded);
        PublicKey bobDecoded = E2EECrypto.decodePublicKey(bobPubEncoded);

        SecretKey aliceShared = E2EECrypto.deriveSharedKey(aliceKeys.getPrivate(), bobDecoded);
        SecretKey bobShared = E2EECrypto.deriveSharedKey(bobKeys.getPrivate(), aliceDecoded);

        assertArrayEquals(aliceShared.getEncoded(), bobShared.getEncoded());

        String plaintext = "Secreto confidencial en Direct Message E2EE!";
        E2EECrypto.EncryptedPayload payload = E2EECrypto.encrypt(plaintext, aliceShared);

        assertNotEquals(plaintext, payload.ciphertextBase64());
        assertNotNull(payload.ivBase64());

        String decrypted = E2EECrypto.decrypt(payload.ciphertextBase64(), payload.ivBase64(), bobShared);
        assertEquals(plaintext, decrypted);

        String fp = E2EECrypto.fingerprint(alicePubEncoded);
        assertTrue(fp.contains(":"));
    }

    @Test
    @DisplayName("Fallo de descifrado con clave equivocada o texto manipulado")
    void testTamperDetection() throws Exception {
        KeyPair alice = E2EECrypto.generateKeyPair();
        KeyPair bob = E2EECrypto.generateKeyPair();
        KeyPair mallory = E2EECrypto.generateKeyPair();

        SecretKey sharedAliceBob = E2EECrypto.deriveSharedKey(alice.getPrivate(), bob.getPublic());
        SecretKey sharedMalloryBob = E2EECrypto.deriveSharedKey(mallory.getPrivate(), bob.getPublic());

        E2EECrypto.EncryptedPayload payload = E2EECrypto.encrypt("Hola Bob", sharedAliceBob);
        assertThrows(Exception.class, () ->
                E2EECrypto.decrypt(payload.ciphertextBase64(), payload.ivBase64(), sharedMalloryBob));
    }
}
