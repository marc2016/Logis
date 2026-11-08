package app.logis.modules.vacationrental;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Free-text agencies and channels become references to the shared list of agencies without losing a value. */
class AgencyMigrationTest {

    @Test
    void everyAgencyAndChannelIsCarriedOver() {
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
            config.target("20261109.1200").load().migrate();
            var db = new JdbcTemplate(ds);
            insert(db, 1, "2026-11-01", "1", "Fewo & Meer", null);
            insert(db, 1, "2026-11-03", "2", "Fewo & Meer", null);
            insert(db, 1, "2026-11-05", "3", "fewo & meer ", null);
            insert(db, 1, "2026-11-07", "4", null, "DIRECT");
            insert(db, 1, "2026-11-09", "5", "Holidu", "AIRBNB");
            insert(db, 1, "2026-11-11", null, null, null);
            db.update("INSERT INTO vacationrental.unit_mappings (owner_user_id, agency, property_name, unit_id) "
                    + "VALUES (7, 'Fewo & Meer', 'Haus Seeufer - Nordhorn', 1), "
                    + "(7, 'FEWO & MEER', 'haus seeufer - nordhorn', 2), "
                    + "(7, 'VVV Nordhorn', 'Ferienhaus Seeufer', 1)");

            config.target("latest").load().migrate();

            assertThat(db.queryForList("SELECT name FROM vacationrental.agencies ORDER BY name", String.class))
                    .containsExactly("Direkt", "Fewo & Meer", "Holidu", "VVV Nordhorn");
            assertThat(agencyOf(db, "1")).isEqualTo("Fewo & Meer");
            assertThat(agencyOf(db, "3")).isEqualTo("Fewo & Meer");
            assertThat(agencyOf(db, "4")).isEqualTo("Direkt");
            // The agency wins over the channel; the channel value stays in its old column.
            assertThat(agencyOf(db, "5")).isEqualTo("Holidu");
            assertThat(db.queryForObject("SELECT channel FROM vacationrental.bookings WHERE external_ref = '5'",
                    String.class)).isEqualTo("AIRBNB");
            assertThat(db.queryForObject("SELECT agency_id FROM vacationrental.bookings WHERE external_ref IS NULL",
                    Long.class)).isNull();
            // Every spelling is a mail name of its agency.
            assertThat(db.queryForList("SELECT al.alias FROM vacationrental.agency_aliases al "
                    + "JOIN vacationrental.agencies a ON a.id = al.agency_id WHERE a.name = 'Fewo & Meer'",
                    String.class)).hasSize(1).allMatch(s -> s.equalsIgnoreCase("Fewo & Meer"));
            // The two mappings that only differed in case collapse into the newer one.
            assertThat(db.queryForList("SELECT unit_id FROM vacationrental.unit_mappings m "
                    + "JOIN vacationrental.agencies a ON a.id = m.agency_id WHERE a.name = 'Fewo & Meer'",
                    Long.class)).containsExactly(2L);
            assertThat(db.queryForObject("SELECT count(*) FROM vacationrental.unit_mappings WHERE agency_id IS NULL",
                    Long.class)).isZero();
            // Booking numbers are unique per agency identity now.
            var fewo = db.queryForObject("SELECT id FROM vacationrental.agencies WHERE name = 'Fewo & Meer'",
                    Long.class);
            assertThatThrownBy(() -> db.update("INSERT INTO vacationrental.bookings "
                    + "(unit_id, arrival, departure, adults, external_ref, agency_id) "
                    + "VALUES (1, DATE '2027-01-01', DATE '2027-01-02', 1, '1', ?)", fewo))
                    .isInstanceOf(DuplicateKeyException.class);
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }

    private static void insert(JdbcTemplate db, long unit, String arrival, String ref, String agency, String channel) {
        db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref, agency, "
                + "channel) VALUES (?, CAST(? AS DATE), CAST(? AS DATE) + 1, 2, ?, ?, ?)", unit, arrival, arrival, ref,
                agency, channel);
    }

    private static String agencyOf(JdbcTemplate db, String ref) {
        return db.queryForObject("SELECT a.name FROM vacationrental.bookings b "
                + "JOIN vacationrental.agencies a ON a.id = b.agency_id WHERE b.external_ref = ?", String.class, ref);
    }
}
