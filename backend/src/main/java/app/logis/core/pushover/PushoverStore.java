package app.logis.core.pushover;

import app.logis.shared.SecretCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** The stored secrets of the channel, encrypted: one application token and a key per user (design D3). */
@Component
class PushoverStore {

    /** @param invalid Pushover rejected the key; nothing is sent to it until the user stores a key again */
    record UserKey(String key, boolean invalid, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final Clock clock;

    PushoverStore(JdbcTemplate jdbc, SecretCipher cipher, Clock clock) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
    }

    Optional<String> token() {
        return jdbc.queryForList("SELECT app_token_enc FROM core.pushover_settings WHERE id = 1", String.class)
                .stream().findFirst().map(cipher::decrypt);
    }

    Optional<Instant> tokenUpdatedAt() {
        return jdbc.queryForList("SELECT updated_at FROM core.pushover_settings WHERE id = 1", Timestamp.class)
                .stream().findFirst().map(Timestamp::toInstant);
    }

    void saveToken(String token) {
        jdbc.update("INSERT INTO core.pushover_settings (id, app_token_enc, updated_at) VALUES (1, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET app_token_enc = EXCLUDED.app_token_enc, "
                        + "updated_at = EXCLUDED.updated_at", cipher.encrypt(token), Timestamp.from(clock.instant()));
    }

    /** The users' keys and switches stay for a later token. */
    void deleteToken() {
        jdbc.update("DELETE FROM core.pushover_settings");
    }

    Optional<UserKey> userKey(Long userId) {
        return jdbc.query("SELECT user_key_enc, invalid_since, updated_at FROM core.pushover_user_keys "
                        + "WHERE user_id = ?", (rs, i) -> new UserKey(cipher.decrypt(rs.getString(1)),
                        rs.getTimestamp(2) != null, rs.getTimestamp(3).toInstant()), userId)
                .stream().findFirst();
    }

    void saveUserKey(Long userId, String key) {
        jdbc.update("INSERT INTO core.pushover_user_keys (user_id, user_key_enc, invalid_since, updated_at) "
                        + "VALUES (?, ?, NULL, ?) ON CONFLICT (user_id) DO UPDATE SET user_key_enc = "
                        + "EXCLUDED.user_key_enc, invalid_since = NULL, updated_at = EXCLUDED.updated_at",
                userId, cipher.encrypt(key), Timestamp.from(clock.instant()));
    }

    void deleteUserKey(Long userId) {
        jdbc.update("DELETE FROM core.pushover_user_keys WHERE user_id = ?", userId);
    }

    void markInvalid(Long userId) {
        jdbc.update("UPDATE core.pushover_user_keys SET invalid_since = ? WHERE user_id = ? "
                + "AND invalid_since IS NULL", Timestamp.from(clock.instant()), userId);
    }
}
