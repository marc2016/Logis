package app.logis.core.mail;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Log of admin mail exports, one row per exported mailbox; never mail content (design D7 of admin-mail-export). */
@Repository
class MailExportLog {

    record Entry(Long id, Instant at, String adminName, Long ownerUserId, String ownerName, int mailCount,
            String filters) {
    }

    private static final String SELECT = "SELECT e.id, e.created_at, a.display_name AS admin_name, e.owner_user_id, "
            + "o.display_name AS owner_name, e.mail_count, e.filters::text AS filters FROM core.mail_exports e "
            + "JOIN core.users a ON a.id = e.admin_user_id JOIN core.users o ON o.id = e.owner_user_id ";

    private final JdbcTemplate jdbc;

    MailExportLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void record(Long adminUserId, Long ownerUserId, Instant at, int mailCount, String filtersJson) {
        jdbc.update("INSERT INTO core.mail_exports (admin_user_id, owner_user_id, created_at, mail_count, filters) "
                + "VALUES (?, ?, ?, ?, ?::jsonb)", adminUserId, ownerUserId, Timestamp.from(at), mailCount, filtersJson);
    }

    Optional<Entry> last(Long ownerUserId) {
        return jdbc.query(SELECT + "WHERE e.owner_user_id = ? ORDER BY e.created_at DESC, e.id DESC LIMIT 1",
                MailExportLog::map, ownerUserId).stream().findFirst();
    }

    List<Entry> all() {
        return jdbc.query(SELECT + "ORDER BY e.created_at DESC, e.id DESC LIMIT 500", MailExportLog::map);
    }

    private static Entry map(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(), rs.getString("admin_name"),
                rs.getLong("owner_user_id"), rs.getString("owner_name"), rs.getInt("mail_count"),
                rs.getString("filters"));
    }
}
