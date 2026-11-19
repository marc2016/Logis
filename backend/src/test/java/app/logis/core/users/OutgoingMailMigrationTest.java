package app.logis.core.users;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The outgoing-mail column and tables are only additions: existing users keep all values, without an address. */
class OutgoingMailMigrationTest {

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
            config.target("20261119.1300").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO core.users (username, display_name, role, password_hash) "
                    + "VALUES ('erika', 'Erika Musterfrau', 'USER', '{noop}x')");
            var before = db.queryForMap("SELECT * FROM core.users");

            config.target("latest").load().migrate();

            var after = db.queryForMap("SELECT * FROM core.users");
            assertThat(after.remove("email")).isNull();
            assertThat(after).isEqualTo(before);
            assertThat(db.queryForObject("SELECT count(*) FROM core.smtp_settings", Long.class)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM core.email_changes", Long.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
