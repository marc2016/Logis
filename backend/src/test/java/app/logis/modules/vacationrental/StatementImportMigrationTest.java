package app.logis.modules.vacationrental;

import app.logis.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Statements recorded before statement-mails keep all their data and get empty new columns. */
class StatementImportMigrationTest {

    @Test
    void existingStatementsAreUnchanged() {
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
            var config = Flyway.configure().dataSource(ds).schemas("core", "vacationrental", "longtermrental")
                    .defaultSchema("core").locations("classpath:db/migration/core",
                            "classpath:db/migration/vacationrental", "classpath:db/migration/longtermrental");
            config.target("20261121.1200").load().migrate();
            var db = new JdbcTemplate(ds);
            var booking = db.queryForObject("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults) "
                    + "VALUES (1, DATE '2026-11-06', DATE '2026-11-08', 2) RETURNING id", Long.class);
            var statement = db.queryForObject("INSERT INTO vacationrental.statements "
                    + "(unit_id, provider, period_from, period_to, statement_date) "
                    + "VALUES (1, 'Nord', DATE '2026-11-01', DATE '2026-11-30', DATE '2026-12-03') RETURNING id",
                    Long.class);
            db.update("INSERT INTO vacationrental.statement_lines (booking_id, statement_id, gross, commission, cleaning) "
                    + "VALUES (?, ?, 410.00, 61.50, 60.00)", booking, statement);

            var result = config.target("latest").load().migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            var row = db.queryForMap("SELECT provider, period_from, statement_date, invoice_number, stated_payout "
                    + "FROM vacationrental.statements WHERE id = ?", statement);
            assertThat(row.get("provider")).isEqualTo("Nord");
            assertThat(row.get("period_from").toString()).isEqualTo("2026-11-01");
            assertThat(row.get("statement_date").toString()).isEqualTo("2026-12-03");
            assertThat(row.get("invoice_number")).isNull();
            assertThat(row.get("stated_payout")).isNull();
            assertThat(db.queryForObject("SELECT gross FROM vacationrental.statement_lines WHERE booking_id = ?",
                    BigDecimal.class, booking)).isEqualByComparingTo("410.00");
            assertThat(db.queryForObject("SELECT count(*) FROM vacationrental.statement_open_lines", Integer.class))
                    .isZero();
        } finally {
            server.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        }
    }
}
