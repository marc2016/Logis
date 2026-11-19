package app.logis.core.mail;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The mail export columns and table are only additions: existing mailboxes keep their values, without consent. */
class MailExportMigrationTest {

    @Test
    void existingMailboxesKeepTheirValuesWithoutConsent() {
        var name = "mig_" + System.nanoTime();
        var server = new JdbcTemplate(new DriverManagerDataSource(TestPostgres.url(), TestPostgres.username(),
                TestPostgres.password()));
        server.execute("CREATE DATABASE " + name);
        try {
            var base = TestPostgres.url();
            var query = base.contains("?") ? base.substring(base.indexOf('?')) : "";
            var path = base.contains("?") ? base.substring(0, base.indexOf('?')) : base;
            var url = path.substring(0, path.lastIndexOf('/') + 1) + name + query;
            var ds = new DriverManagerDataSource(url, TestPostgres.username(), TestPostgres.password());
            var config = Flyway.configure().dataSource(ds).schemas("core", "vacationrental").defaultSchema("core")
                    .locations("classpath:db/migration/core", "classpath:db/migration/vacationrental");
            config.target("20261119.1200").load().migrate();
            var db = new JdbcTemplate(ds);
            var userId = db.queryForObject("INSERT INTO core.users (username, display_name, role, password_hash) "
                    + "VALUES ('erika', 'Erika Musterfrau', 'USER', '{noop}x') RETURNING id", Long.class);
            db.update("INSERT INTO core.mailboxes (owner_user_id, host, port, username, password_enc, cutoff_date) "
                    + "VALUES (?, 'imap.example.org', 993, 'erika@example.org', 'enc', DATE '2026-11-01')", userId);
            var before = db.queryForMap("SELECT * FROM core.mailboxes");

            config.target("latest").load().migrate();

            var after = db.queryForMap("SELECT * FROM core.mailboxes");
            assertThat(after.remove("admin_export_allowed")).isEqualTo(false);
            assertThat(after.remove("admin_export_allowed_at")).isNull();
            assertThat(after).isEqualTo(before);
            assertThat(db.queryForObject("SELECT count(*) FROM core.mail_exports", Long.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
