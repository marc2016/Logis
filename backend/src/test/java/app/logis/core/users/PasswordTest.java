package app.logis.core.users;

import app.logis.core.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    private final org.springframework.security.crypto.password.PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void hashIsSaltedAndNotPlaintext() {
        var hash = encoder.encode("ein-langes-passwort");
        assertThat(hash).doesNotContain("ein-langes-passwort").startsWith("{bcrypt}");
        assertThat(encoder.encode("ein-langes-passwort")).isNotEqualTo(hash);
        assertThat(encoder.matches("ein-langes-passwort", hash)).isTrue();
        assertThat(encoder.matches("falsch-falsch-falsch", hash)).isFalse();
    }

    @Test
    void minimumLengthIsTwelve() {
        assertThat(PasswordPolicy.isValid("123456789012")).isTrue();
        assertThat(PasswordPolicy.isValid("12345678901")).isFalse();
        assertThatThrownBy(() -> PasswordPolicy.requireValid("12345678901"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mindestens 12 Zeichen");
    }
}
