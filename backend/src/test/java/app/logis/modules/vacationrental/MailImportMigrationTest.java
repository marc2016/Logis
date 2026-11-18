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
                    + "VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2, '250905')");

            // Up to the mail import; later migrations replace the free-text agency (see AgencyMigrationTest).
            config.target("20261109.1200").load().migrate();

            var row = db.queryForMap("SELECT review_state, agency, pets, proposal FROM vacationrental.bookings");
            assertThat(row.get("review_state")).isEqualTo("NONE");
            assertThat(row.get("agency")).isNull();
            assertThat(((Number) row.get("pets")).intValue()).isZero();
            assertThat(row.get("proposal")).isNull();
            // Same number at another agency on the same unit is allowed now.
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref, agency) "
                    + "VALUES (1, DATE '2026-12-06', DATE '2026-12-08', 2, '250905', 'Holidu')");
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }

    @Test
    void existingBookingsBecomeGuestBookings() {
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
            config.target("20261114.1200").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref) "
                    + "VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2, '250905')");

            config.target("20261118.1000").load().migrate();

            var row = db.queryForMap("SELECT kind, adults, external_ref FROM vacationrental.bookings");
            assertThat(row.get("kind")).isEqualTo("GUEST");
            assertThat(((Number) row.get("adults")).intValue()).isEqualTo(2);
            assertThat(row.get("external_ref")).isEqualTo("250905");
            // Only guest bookings need adults.
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, kind) "
                    + "VALUES (1, DATE '2027-06-09', DATE '2027-06-14', 0, 'OWNER')");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> db.update(
                    "INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults) "
                            + "VALUES (1, DATE '2027-07-09', DATE '2027-07-14', 0)"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
