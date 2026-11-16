package app.logis.core.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Passkeys and WebAuthn user handles (design.md D3 of passkeys). */
@Repository
class PasskeyStore {

    record StoredPasskey(Long id, Long userId, byte[] credentialId, byte[] publicKeyCose, long signCount,
            String transports, String name, Instant createdAt, Instant lastUsedAt) {
    }

    private static final String COLUMNS =
            "id, user_id, credential_id, public_key_cose, sign_count, transports, name, created_at, last_used_at";

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    PasskeyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<byte[]> userHandle(Long userId) {
        return jdbc.query("SELECT webauthn_user_handle FROM core.users WHERE id = ?", (rs, i) -> rs.getBytes(1), userId)
                .stream().filter(h -> h != null).findFirst();
    }

    /** The user's handle, created on first use: 32 random bytes. */
    byte[] userHandleCreating(Long userId) {
        return userHandle(userId).orElseGet(() -> {
            var handle = new byte[32];
            random.nextBytes(handle);
            jdbc.update("UPDATE core.users SET webauthn_user_handle = ? WHERE id = ? AND webauthn_user_handle IS NULL",
                    handle, userId);
            return userHandle(userId).orElseThrow();
        });
    }

    Optional<byte[]> userHandleForUsername(String username) {
        return jdbc.query("SELECT webauthn_user_handle FROM core.users WHERE lower(username) = lower(?)",
                (rs, i) -> rs.getBytes(1), username).stream().filter(h -> h != null).findFirst();
    }

    Optional<String> usernameForUserHandle(byte[] handle) {
        return jdbc.query("SELECT username FROM core.users WHERE webauthn_user_handle = ?", (rs, i) -> rs.getString(1),
                handle).stream().findFirst();
    }

    Optional<Long> userIdForUserHandle(byte[] handle) {
        return jdbc.query("SELECT id FROM core.users WHERE webauthn_user_handle = ?", (rs, i) -> rs.getLong(1), handle)
                .stream().findFirst();
    }

    List<StoredPasskey> forUser(Long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM core.passkeys WHERE user_id = ? ORDER BY created_at, id",
                PasskeyStore::map, userId);
    }

    List<StoredPasskey> forUsername(String username) {
        return jdbc.query("SELECT " + COLUMNS.replaceAll("(\\w+)", "p.$1") + " FROM core.passkeys p "
                + "JOIN core.users u ON u.id = p.user_id WHERE lower(u.username) = lower(?)", PasskeyStore::map, username);
    }

    Optional<StoredPasskey> byCredentialId(byte[] credentialId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM core.passkeys WHERE credential_id = ?", PasskeyStore::map,
                credentialId).stream().findFirst();
    }

    Long insert(Long userId, byte[] credentialId, byte[] publicKeyCose, long signCount, String transports, String name,
            Instant now) {
        return jdbc.queryForObject("INSERT INTO core.passkeys "
                        + "(user_id, credential_id, public_key_cose, sign_count, transports, name, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, userId, credentialId, publicKeyCose,
                signCount, transports, name, Timestamp.from(now));
    }

    void recordUse(Long id, long signCount, Instant now) {
        jdbc.update("UPDATE core.passkeys SET sign_count = ?, last_used_at = ? WHERE id = ?", signCount,
                Timestamp.from(now), id);
    }

    boolean rename(Long userId, Long id, String name) {
        return jdbc.update("UPDATE core.passkeys SET name = ? WHERE id = ? AND user_id = ?", name, id, userId) == 1;
    }

    boolean delete(Long userId, Long id) {
        return jdbc.update("DELETE FROM core.passkeys WHERE id = ? AND user_id = ?", id, userId) == 1;
    }

    void deleteAll(Long userId) {
        jdbc.update("DELETE FROM core.passkeys WHERE user_id = ?", userId);
    }

    private static StoredPasskey map(ResultSet rs, int row) throws SQLException {
        var used = rs.getTimestamp("last_used_at");
        return new StoredPasskey(rs.getLong("id"), rs.getLong("user_id"), rs.getBytes("credential_id"),
                rs.getBytes("public_key_cose"), rs.getLong("sign_count"), rs.getString("transports"),
                rs.getString("name"), rs.getTimestamp("created_at").toInstant(), used == null ? null : used.toInstant());
    }
}
