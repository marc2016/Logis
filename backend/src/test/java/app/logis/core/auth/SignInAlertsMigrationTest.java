package app.logis.core.auth;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The sign-in history and device locations are only additions: existing rows keep their values. */
class SignInAlertsMigrationTest {

    /** The device row with binary columns as text, so that maps compare by value. */
    private static final String DEVICE = "SELECT id, user_id, encode(series, 'hex') AS series, encode(token_hash, 'hex') AS token_hash, session_version, browser, os, created_at, last_used_at, expires_at FROM core.remembered_devices";

    @Test
    void existingUsersAndDevicesKeepTheirValues() {
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
            config.target("20261119.1400").load().migrate();
            var db = new JdbcTemplate(ds);
            var userId = db.queryForObject("INSERT INTO core.users (username, display_name, role, password_hash, email) "
                    + "VALUES ('erika', 'Erika Musterfrau', 'USER', '{noop}x', 'erika@example.org') RETURNING id",
                    Long.class);
            db.update("INSERT INTO core.remembered_devices (user_id, series, token_hash, session_version, browser, os, "
                    + "last_used_at, expires_at) VALUES (?, '\\x01'::bytea, '\\x02'::bytea, 0, 'Firefox', 'Linux', now(), "
                    + "now() + interval '90 days')", userId);
            var users = db.queryForMap("SELECT * FROM core.users");
            var device = db.queryForMap(DEVICE);

            config.target("latest").load().migrate();

            assertThat(db.queryForMap("SELECT * FROM core.users")).isEqualTo(users);
            assertThat(db.queryForMap(DEVICE)).isEqualTo(device);
            assertThat(db.queryForMap("SELECT country, city FROM core.remembered_devices").values()).containsOnlyNulls();
            assertThat(db.queryForObject("SELECT count(*) FROM core.sign_ins", Long.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
