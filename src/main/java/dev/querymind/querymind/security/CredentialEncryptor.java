package dev.querymind.querymind.security;

import jakarta.annotation.PostConstruct;
import org.jasypt.util.text.AES256TextEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts database passwords before we store them.
 *
 * We never want to keep a user's database password as plain text in our
 * own database, so we run it through AES encryption first. The master
 * key comes from the "querymind.encryption.key" property (set it from an
 * environment variable in production).
 */
@Component
public class CredentialEncryptor {

    @Value("${querymind.encryption.key}")
    private String masterKey;

    // Jasypt helper that does the actual AES work.
    private final AES256TextEncryptor encryptor = new AES256TextEncryptor();

    @PostConstruct
    public void init() {
        encryptor.setPassword(masterKey);
    }

    // Turn a plain password into encrypted text (safe to store).
    public String encrypt(String plainText) {
        return encryptor.encrypt(plainText);
    }

    // Turn the encrypted text back into the plain password (to connect).
    public String decrypt(String encryptedText) {
        return encryptor.decrypt(encryptedText);
    }
}
