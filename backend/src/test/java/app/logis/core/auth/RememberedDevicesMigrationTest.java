package app.logis.core.auth;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The remembered-device table is only an addition: existing users stay as they are. */
class RememberedDevicesMigrationTest {

    @Test
    void existingUsersStayUnchanged() {
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
            config.target("20261118.1000").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO core.users (username, display_name, role, password_hash) "
                    + "VALUES ('erika', 'Erika Musterfrau', 'USER', '{noop}x')");
            var before = db.queryForMap("SELECT * FROM core.users");

            config.target("latest").load().migrate();

            var after = db.queryForMap("SELECT * FROM core.users");
            // Added by a later migration (outgoing-mail), checked in OutgoingMailMigrationTest.
            after.remove("email");
            assertThat(after).isEqualTo(before);
            assertThat(db.queryForObject("SELECT count(*) FROM core.remembered_devices", Long.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
