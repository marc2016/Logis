package app.logis.core.ledger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationsTest extends LedgerTest {

    @Test
    void defaultCategoriesExist() {
        var rows = jdbc.queryForList("SELECT c.name, c.kind, p.name AS parent FROM core.categories c "
                + "LEFT JOIN core.categories p ON p.id = c.parent_id WHERE c.module_key IS NULL");
        assertThat(rows).extracting(r -> r.get("kind") + ":" + r.get("parent") + ":" + r.get("name"))
                .contains("INCOME:null:Gehalt", "INCOME:null:Mieteinnahmen", "INCOME:null:Kapitalerträge",
                        "INCOME:null:Sonstige Einnahmen", "EXPENSE:null:Wohnen", "EXPENSE:Wohnen:Grundsteuer",
                        "EXPENSE:Wohnen:Nebenkosten", "EXPENSE:Wohnen:Instandhaltung", "EXPENSE:null:Lebensmittel",
                        "EXPENSE:null:Mobilität", "EXPENSE:null:Versicherungen", "EXPENSE:null:Steuern und Abgaben",
                        "EXPENSE:null:Gebühren", "EXPENSE:null:Sonstige Ausgaben");
    }

    /** Separate database: create users before the migration, then migrate. */
    @Test
    void existingUsersGetPersonalLegalEntity() {
        try (var pg = new PostgreSQLContainer<>("postgres:17")) {
            pg.start();
            var ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            var config = Flyway.configure().dataSource(ds).schemas("core", "vacationrental").defaultSchema("core")
                    .locations("classpath:db/migration/core", "classpath:db/migration/vacationrental");
            config.target("20261026.1201").load().migrate();

            var db = new JdbcTemplate(ds);
            for (var name : new String[] {"Anna", "Bert"}) {
                db.update("INSERT INTO core.users (username, display_name, role, password_hash) VALUES (?, ?, 'USER', 'x')",
                        name.toLowerCase(), name);
            }

            config.target("latest").load().migrate();

            var entities = db.queryForList("SELECT u.username, le.name, le.kind FROM core.legal_entities le "
                    + "JOIN core.users u ON u.id = le.manager_user_id WHERE le.personal ORDER BY u.username");
            assertThat(entities).containsExactly(
                    Map.of("username", "anna", "name", "Anna privat", "kind", "PRIVATE_PERSON"),
                    Map.of("username", "bert", "name", "Bert privat", "kind", "PRIVATE_PERSON"));
        }
    }
}
