package app.logis.modules.vacationrental;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The source tables are added without touching existing bookings. */
class BookingSourcesMigrationTest {

    @Test
    void existingBookingsStayUnchanged() {
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
            config.target("20261110.1100").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref, "
                    + "guest_name) VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2, '250905', 'Erika Musterfrau')");
            var before = db.queryForMap("SELECT * FROM vacationrental.bookings");

            // Up to the booking sources; the later booking kind is covered by MailImportMigrationTest.
            config.target("20261114.1200").load().migrate();

            assertThat(db.queryForMap("SELECT * FROM vacationrental.bookings")).isEqualTo(before);
            assertThat(db.queryForObject("SELECT count(*) FROM vacationrental.booking_sources", Long.class)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM vacationrental.booking_field_sources", Long.class))
                    .isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
