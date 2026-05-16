package com.satyajit.upioffline.crypto;

/*
 * Generates and holds the server RSA-2048 keypair.
 * In production the private key lives in an HSM or KMS — never in the JAR.
 * Here we generate fresh on every startup for demo purposes.
 * Public key is exposed via /api/server-key so sender devices can encrypt payloads.
 */

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Slf4j
@Component
public class ServerKeyHolder {

    private KeyPair keyPair;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        log.info("RSA-2048 keypair ready. Public key prefix: {}",
                getPublicKeyBase64().substring(0, 32) + "...");
    }

    public PublicKey getPublicKey() { return keyPair.getPublic(); }

    public PrivateKey getPrivateKey() { return keyPair.getPrivate(); }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}