package com.satyajit.upioffline.crypto;

/*
 * Hybrid encryption — RSA-OAEP wraps a one-time AES-256 key,
 * AES-GCM encrypts the actual payload.
 *
 * Wire format (base64 decoded):
 *   [256 bytes RSA-encrypted AES key][12 bytes IV][AES-GCM ciphertext + 16-byte tag]
 *
 * AES-GCM is authenticated — any tampering causes decryption to throw.
 * This is the same scheme TLS uses internally.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.satyajit.upioffline.model.PaymentInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
public class HybridCryptoService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int RSA_ENCRYPTED_KEY_BYTES = 256; // 2048-bit RSA produces 256-byte output

    private final SecureRandom rng = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ServerKeyHolder serverKey;

    // Called by the simulated sender device to encrypt a payment before injecting into mesh
    public String encrypt(PaymentInstruction instruction, PublicKey serverPublicKey) throws Exception {
        byte[] plaintext = json.writeValueAsBytes(instruction);

        // generate a one-time AES key for this packet only
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS);
        SecretKey aesKey = kg.generateKey();

        // AES-GCM encrypt the payload
        byte[] iv = new byte[GCM_IV_BYTES];
        rng.nextBytes(iv);
        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] aesCiphertext = aes.doFinal(plaintext);

        // RSA-OAEP encrypt the AES key — only the server's private key can unwrap this
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.ENCRYPT_MODE, serverPublicKey, oaep);
        byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());

        // pack: [encrypted AES key][IV][AES ciphertext + GCM tag]
        ByteBuffer buf = ByteBuffer.allocate(
                encryptedAesKey.length + iv.length + aesCiphertext.length);
        buf.put(encryptedAesKey);
        buf.put(iv);
        buf.put(aesCiphertext);

        return Base64.getEncoder().encodeToString(buf.array());
    }

    // Called by the server to decrypt an incoming packet from a bridge node
    public PaymentInstruction decrypt(String base64Ciphertext) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64Ciphertext);

        if (all.length < RSA_ENCRYPTED_KEY_BYTES + GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new IllegalArgumentException("Ciphertext too short");
        }

        byte[] encryptedAesKey = new byte[RSA_ENCRYPTED_KEY_BYTES];
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] aesCiphertext = new byte[all.length - RSA_ENCRYPTED_KEY_BYTES - GCM_IV_BYTES];

        ByteBuffer buf = ByteBuffer.wrap(all);
        buf.get(encryptedAesKey);
        buf.get(iv);
        buf.get(aesCiphertext);

        // unwrap the AES key using server's private key
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.DECRYPT_MODE, serverKey.getPrivateKey(), oaep);
        byte[] aesKeyBytes = rsa.doFinal(encryptedAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        // AES-GCM decrypt — throws if tampered
        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = aes.doFinal(aesCiphertext);

        return json.readValue(plaintext, PaymentInstruction.class);
    }

    // SHA-256 of the ciphertext — used as the idempotency key in IdempotencyService
    // intermediates can rewrite packetId but cannot forge a valid ciphertext
    public String hashCiphertext(String base64Ciphertext) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(base64Ciphertext.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}