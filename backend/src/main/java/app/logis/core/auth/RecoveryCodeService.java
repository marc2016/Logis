package app.logis.core.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-use recovery codes (10 codes, format XXXXX-XXXXX), stored only as hashes.
 */
@Service
public class RecoveryCodeService {

    static final int COUNT = 10;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RecoveryCodeRepository codes;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RecoveryCodeService(RecoveryCodeRepository codes, PasswordEncoder encoder, Clock clock) {
        this.codes = codes;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Replaces all previous codes and returns the new ones in plain text (this one time only). */
    @Transactional
    public List<String> regenerate(Long userId) {
        codes.deleteAllForUser(userId);
        var plain = new ArrayList<String>(COUNT);
        var now = clock.instant();
        for (int i = 0; i < COUNT; i++) {
            var code = randomCode();
            plain.add(code.substring(0, 5) + "-" + code.substring(5));
            codes.save(new RecoveryCode(userId, encoder.encode(code), now));
        }
        return plain;
    }

    @Transactional
    public void deleteAll(Long userId) {
        codes.deleteAllForUser(userId);
    }

    /** Checks the code and invalidates it on success. */
    @Transactional
    boolean consume(Long userId, String input) {
        var normalized = normalize(input);
        if (normalized.length() != 10) {
            return false;
        }
        for (var code : codes.findUnused(userId)) {
            if (encoder.matches(normalized, code.getCodeHash())) {
                code.markUsed(clock.instant());
                return true;
            }
        }
        return false;
    }

    static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String randomCode() {
        var sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
