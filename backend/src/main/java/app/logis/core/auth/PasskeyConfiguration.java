package app.logis.core.auth;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Set;

/**
 * The WebAuthn relying party, bound to the public address {@code logis.auth.public-url}: its host is the RP ID and
 * its origin the only accepted one. Without the setting there are no passkeys (design.md D2 of passkeys).
 */
@Configuration
@ConditionalOnExpression("'${logis.auth.public-url:}' != ''")
class PasskeyConfiguration {

    @Bean
    RelyingParty relyingParty(@Value("${logis.auth.public-url}") String publicUrl,
            PasskeyCredentialRepository credentials) {
        var uri = URI.create(publicUrl.trim());
        if (uri.getHost() == null || uri.getScheme() == null) {
            throw new IllegalStateException("LOGIS_PUBLIC_URL must be an address like https://logis.example.org");
        }
        var origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(uri.getHost()).name("Logis").build())
                .credentialRepository(credentials)
                .origins(Set.of(origin))
                .build();
    }
}
