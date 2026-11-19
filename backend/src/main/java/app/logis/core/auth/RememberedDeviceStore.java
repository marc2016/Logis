package app.logis.core.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Rows of {@code core.remembered_devices} (design.md of remember-me, D11). */
@Repository
class RememberedDeviceStore {

    record Device(Long id, Long userId, byte[] series, byte[] tokenHash, byte[] previousTokenHash, Instant rotatedAt,
            int sessionVersion, String browser, String os, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
    }

    private static final String COLUMNS = "id, user_id, series, token_hash, previous_token_hash, rotated_at, "
            + "session_version, browser, os, created_at, last_used_at, expires_at";

    private final JdbcTemplate jdbc;

    RememberedDeviceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Long insert(Long userId, byte[] series, byte[] tokenHash, int sessionVersion, DeviceFamily family, Instant now,
            Instant expiresAt) {
        return jdbc.queryForObject("INSERT INTO core.remembered_devices (user_id, series, token_hash, session_version, "
                        + "browser, os, created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, userId, series, tokenHash, sessionVersion, family.browser(), family.os(),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(expiresAt));
    }

    /** Locks the row, so that two parallel restores cannot both rotate (design D3). */
    Optional<Device> bySeriesForUpdate(byte[] series) {
        return jdbc.query("SELECT " + COLUMNS + " FROM core.remembered_devices WHERE series = ? FOR UPDATE",
                RememberedDeviceStore::map, series).stream().findFirst();
    }

    Optional<Device> byId(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM core.remembered_devices WHERE id = ?",
                RememberedDeviceStore::map, id).stream().findFirst();
    }

    void rotate(Long id, byte[] tokenHash, byte[] previousTokenHash, Instant now) {
        jdbc.update("UPDATE core.remembered_devices SET token_hash = ?, previous_token_hash = ?, rotated_at = ?, "
                + "last_used_at = ? WHERE id = ?", tokenHash, previousTokenHash, Timestamp.from(now), Timestamp.from(now), id);
    }

    void touch(Long id, Instant now) {
        jdbc.update("UPDATE core.remembered_devices SET last_used_at = ? WHERE id = ?", Timestamp.from(now), id);
    }

    void updateSessionVersion(Long id, int sessionVersion) {
        jdbc.update("UPDATE core.remembered_devices SET session_version = ? WHERE id = ?", sessionVersion, id);
    }

    void delete(Long id) {
        jdbc.update("DELETE FROM core.remembered_devices WHERE id = ?", id);
    }

    void deleteBySeries(byte[] series) {
        jdbc.update("DELETE FROM core.remembered_devices WHERE series = ?", (Object) series);
    }

    /** Returns whether a device of this user was deleted. */
    boolean deleteOwn(Long userId, Long id) {
        return jdbc.update("DELETE FROM core.remembered_devices WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    /** Deletes all devices of the user except {@code keepId} (may be null). */
    void deleteOthers(Long userId, Long keepId) {
        if (keepId == null) {
            jdbc.update("DELETE FROM core.remembered_devices WHERE user_id = ?", userId);
        } else {
            jdbc.update("DELETE FROM core.remembered_devices WHERE user_id = ? AND id <> ?", userId, keepId);
        }
    }

    /** Devices that still belong to the user's current session version. */
    List<Device> current(Long userId, int sessionVersion) {
        return jdbc.query("SELECT " + COLUMNS + " FROM core.remembered_devices WHERE user_id = ? AND session_version = ? "
                + "ORDER BY last_used_at DESC, id DESC", RememberedDeviceStore::map, userId, sessionVersion);
    }

    /** Deletes devices past either duration and devices whose session version is outdated; returns the count. */
    int deleteStale(Instant now, Instant unusedSince) {
        return jdbc.update("DELETE FROM core.remembered_devices d WHERE d.expires_at < ? OR d.last_used_at < ? "
                        + "OR d.session_version <> (SELECT u.session_version FROM core.users u WHERE u.id = d.user_id)",
                Timestamp.from(now), Timestamp.from(unusedSince));
    }

    private static Device map(ResultSet rs, int row) throws SQLException {
        var rotated = rs.getTimestamp("rotated_at");
        return new Device(rs.getLong("id"), rs.getLong("user_id"), rs.getBytes("series"), rs.getBytes("token_hash"),
                rs.getBytes("previous_token_hash"), rotated == null ? null : rotated.toInstant(),
                rs.getInt("session_version"), rs.getString("browser"), rs.getString("os"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_used_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant());
    }
}
