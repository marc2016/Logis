package app.logis.core.realestate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealestateMigrationTest {

    /** Separate database: create a transaction before the migration, then migrate. */
    @Test
    void existingTransactionsKeepNoTarget() {
        try (var pg = new PostgreSQLContainer<>("postgres:17")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            var config = Flyway.configure().dataSource(ds).schemas("core", "vacationrental").defaultSchema("core")
                    .locations("classpath:db/migration/core", "classpath:db/migration/vacationrental");
            config.target("20261026.1202").load().migrate();

            var db = new JdbcTemplate(ds);
            db.update("INSERT INTO core.users (username, display_name, role, password_hash) VALUES ('anna', 'Anna', 'USER', 'x')");
            db.update("INSERT INTO core.legal_entities (name, kind, manager_user_id, personal) "
                    + "SELECT 'Anna privat', 'PRIVATE_PERSON', id, true FROM core.users");
            db.update("INSERT INTO core.accounts (legal_entity_id, name, kind) SELECT id, 'Giro', 'CHECKING' FROM core.legal_entities");
            db.update("INSERT INTO core.transactions (account_id, booking_date, amount, created_by, updated_by) "
                    + "SELECT a.id, DATE '2026-10-01', -12.50, u.id, u.id FROM core.accounts a, core.users u");

            config.target("latest").load().migrate();

            assertThat(db.queryForObject("SELECT count(*) FROM core.transactions "
                    + "WHERE property_id IS NULL AND unit_id IS NULL", Integer.class)).isEqualTo(1);

            db.update("INSERT INTO core.properties (legal_entity_id, name) SELECT id, 'Haus' FROM core.legal_entities");
            db.update("INSERT INTO core.units (property_id, name, usage) SELECT id, 'Whg. 1', 'OTHER' FROM core.properties");
            assertThatThrownBy(() -> db.update("UPDATE core.transactions SET "
                    + "property_id = (SELECT id FROM core.properties), unit_id = (SELECT id FROM core.units)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
