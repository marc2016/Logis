package app.logis.core.preferences;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The settings table is only an addition; a user's settings go with the user. */
class PreferencesMigrationTest {

    @Test
    void existingUsersKeepTheirValuesAndTilesGoWithTheUser() {
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
            config.target("20261120.1000").load().migrate();
            var db = new JdbcTemplate(ds);
            var userId = db.queryForObject("INSERT INTO core.users (username, display_name, role, password_hash, email) "
                    + "VALUES ('erika', 'Erika Musterfrau', 'USER', '{noop}x', 'erika@example.org') RETURNING id",
                    Long.class);
            var users = db.queryForMap("SELECT * FROM core.users");

            config.target("latest").load().migrate();

            assertThat(db.queryForMap("SELECT * FROM core.users")).isEqualTo(users);
            assertThat(db.queryForObject("SELECT count(*) FROM core.user_preferences", Long.class)).isZero();
            db.update("INSERT INTO core.user_preferences (user_id, key, value, updated_at) VALUES (?, 'core.view', '1'::jsonb, now())", userId);
            db.update("DELETE FROM core.users WHERE id = ?", userId);
            assertThat(db.queryForObject("SELECT count(*) FROM core.user_preferences", Long.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
