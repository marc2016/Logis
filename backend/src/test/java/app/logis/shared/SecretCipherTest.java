package app.logis.shared;

import app.logis.LogisApplication;
import app.logis.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.ArrayList;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTrip() {
        var cipher = new SecretCipher(KEY);
        var enc = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(enc).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.encrypt("JBSWY3DPEHPK3PXP")).isNotEqualTo(enc);
        assertThat(cipher.decrypt(enc)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        var cipher = new SecretCipher(KEY);
        var raw = Base64.getDecoder().decode(cipher.encrypt("geheim"));
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidKeysAreRejected() {
        assertThatThrownBy(() -> new SecretCipher("")).hasMessageContaining("LOGIS_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new SecretCipher("kein base64!")).hasMessageContaining("Base64");
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 Byte");
    }

    @Test
    void startupFailsWithoutKey() {
        var args = new ArrayList<>(TestPostgres.args());
        args.add("--server.port=0");
        args.add("--logis.encryption-key=");
        assertThatThrownBy(() -> new SpringApplicationBuilder(LogisApplication.class).profiles("test")
                .run(args.toArray(String[]::new)))
                .rootCause()
                .hasMessageContaining("LOGIS_ENCRYPTION_KEY ist nicht gesetzt");
    }
}
