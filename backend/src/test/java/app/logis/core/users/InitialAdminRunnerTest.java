package app.logis.core.users;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialAdminRunnerTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    org.springframework.context.ApplicationEventPublisher events;

    @BeforeEach
    void emptyDatabase() {
        jdbc.update("DELETE FROM core.object_shares");
        jdbc.update("DELETE FROM core.transactions");
        jdbc.update("DELETE FROM core.accounts");
        jdbc.update("DELETE FROM core.legal_entities");
        jdbc.update("DELETE FROM core.users");
    }

    private void run(String username, String password) {
        var runner = new InitialAdminRunner(users, encoder, clock, events, username, password);
        tx.executeWithoutResult(s -> runner.run(new DefaultApplicationArguments()));
    }

    @Test
    void createsAdminOnFirstStart() {
        run("chef", "ein-sicheres-passwort");
        var admin = users.findByUsernameIgnoreCase("chef").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isTotpEnabled()).isFalse();
        assertThat(admin.getPasswordHash()).doesNotContain("ein-sicheres-passwort");
        assertThat(encoder.matches("ein-sicheres-passwort", admin.getPasswordHash())).isTrue();
    }

    @Test
    void failsWithoutConfiguration() {
        assertThatThrownBy(() -> run("", ""))
                .hasMessageContaining("LOGIS_ADMIN_USERNAME")
                .hasMessageContaining("LOGIS_ADMIN_PASSWORD");
    }

    @Test
    void failsWithShortPassword() {
        assertThatThrownBy(() -> run("chef", "kurz1234"))
                .hasMessageContaining("mindestens 12 Zeichen");
    }

    @Test
    void ignoresConfigurationWhenUsersExist() {
        run("chef", "ein-sicheres-passwort");
        run("chef", "ein-anderes-passwort");
        run("", "");
        var admin = users.findByUsernameIgnoreCase("chef").orElseThrow();
        assertThat(encoder.matches("ein-sicheres-passwort", admin.getPasswordHash())).isTrue();
        assertThat(users.count()).isEqualTo(1);
    }
}
