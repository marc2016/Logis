package app.logis.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Verschluesselt kleine Geheimnisse (z. B. TOTP-Secrets) mit AES-256-GCM.
 * Format: Base64(IV[12] || Ciphertext+Tag).
 */
@Component
public class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${logis.encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "LOGIS_ENCRYPTION_KEY ist nicht gesetzt. Erzeugen mit: openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("LOGIS_ENCRYPTION_KEY ist kein gueltiges Base64.");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("LOGIS_ENCRYPTION_KEY muss genau 32 Byte lang sein (ist " + raw.length + ").");
        }
        return new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Verschluesselung fehlgeschlagen", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] pt = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Entschluesselung fehlgeschlagen", ex);
        }
    }
}
