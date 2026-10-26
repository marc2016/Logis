package app.logis.core.auth;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.OptionalLong;

/**
 * TOTP nach RFC 6238: SHA-1, 6 Stellen, 30 s, Toleranz +-1 Zeitschritt.
 * Ein Code ist nur gueltig, wenn sein Zeitschritt groesser als der zuletzt verwendete ist (Replay-Schutz).
 */
@Component
class TotpService {

    static final int PERIOD_SECONDS = 30;
    private static final String ISSUER = "Logis";

    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final Clock clock;

    TotpService(Clock clock) {
        this.clock = clock;
    }

    String generateSecret() {
        return secretGenerator.generate();
    }

    String otpauthUri(String username, String secret) {
        var label = encode(ISSUER) + ":" + encode(username);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=6&period=" + PERIOD_SECONDS;
    }

    /**
     * @param lastUsedStep zuletzt erfolgreich verwendeter Zeitschritt oder {@code null}
     * @return der Zeitschritt des passenden Codes, sonst leer
     */
    OptionalLong verify(String secret, String code, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        long current = clock.instant().getEpochSecond() / PERIOD_SECONDS;
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (MessageDigest.isEqual(generate(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    String generate(String secret, long step) {
        try {
            return codeGenerator.generate(secret, step);
        } catch (CodeGenerationException ex) {
            throw new IllegalStateException("TOTP-Code konnte nicht erzeugt werden", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
