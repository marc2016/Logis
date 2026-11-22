package app.logis.modules.longtermrental;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The module's first migration only adds its own schema: existing data of the other schemas is unchanged. */
class TenanciesMigrationTest {

    @Test
    void existingDataIsUnchanged() {
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
            // A deployment before this change: the schema did not exist and had no migration location.
            Flyway.configure().dataSource(ds).schemas("core", "vacationrental").defaultSchema("core")
                    .locations("classpath:db/migration/core", "classpath:db/migration/vacationrental")
                    // The last migration before long-term rental; later ones belong to later deployments.
                    .target("20261121.1000").load().migrate();
            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, external_ref) "
                    + "VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2, 'R-1')");
            var tables = tables(db);

            var result = Flyway.configure().dataSource(ds).schemas("core", "vacationrental", "longtermrental")
                    .defaultSchema("core")
                    .locations("classpath:db/migration/core", "classpath:db/migration/vacationrental",
                            "classpath:db/migration/longtermrental")
                    .target("20261121.1200").load().migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(tables(db)).isEqualTo(tables);
            assertThat(db.queryForObject("SELECT external_ref FROM vacationrental.bookings", String.class))
                    .isEqualTo("R-1");
            assertThat(db.queryForList("SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = 'longtermrental' ORDER BY table_name", String.class))
                    .containsExactly("rent_items", "rent_terms", "tenancies", "tenants");
            assertThat(db.queryForObject("SELECT count(*) FROM longtermrental.tenancies", Integer.class)).isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }

    /** Every column of the existing schemas, to see that none was touched. */
    private static java.util.List<String> tables(JdbcTemplate db) {
        return db.queryForList("SELECT table_schema || '.' || table_name || '.' || column_name || ' ' || data_type "
                + "FROM information_schema.columns WHERE table_schema IN ('core', 'vacationrental') "
                + "AND table_name <> 'flyway_schema_history' ORDER BY 1", String.class);
    }
}
