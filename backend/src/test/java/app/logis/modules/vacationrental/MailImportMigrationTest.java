package app.logis.modules.vacationrental;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Existing bookings survive the mail import migration unchanged and without review. */
class MailImportMigrationTest {

    @Test
    void existingBookingsGetNoReview() {
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
            config.target("20261107.1210").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref) "
                    + "VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2, '275505')");

            config.target("latest").load().migrate();

            var row = db.queryForMap("SELECT review_state, agency, pets, proposal FROM vacationrental.bookings");
            assertThat(row.get("review_state")).isEqualTo("NONE");
            assertThat(row.get("agency")).isNull();
            assertThat(((Number) row.get("pets")).intValue()).isZero();
            assertThat(row.get("proposal")).isNull();
            // Same number at another agency on the same unit is allowed now.
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref, agency) "
                    + "VALUES (1, DATE '2026-12-06', DATE '2026-12-08', 2, '275505', 'Holidu')");
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
