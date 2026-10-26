package app.logis.core.web;

import app.logis.LogisApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Eigener Container, damit er gestoppt werden kann, ohne andere Tests zu stoeren. */
class HealthDatabaseDownTest {

    @Test
    void reports503WhenDatabaseIsDown() {
        try (var postgres = new PostgreSQLContainer<>("postgres:17")) {
            postgres.start();
            try (var ctx = new SpringApplicationBuilder(LogisApplication.class)
                    .profiles("test")
                    .run("--server.port=0",
                            "--spring.datasource.url=" + postgres.getJdbcUrl(),
                            "--spring.datasource.username=" + postgres.getUsername(),
                            "--spring.datasource.password=" + postgres.getPassword(),
                            "--spring.datasource.hikari.connection-timeout=1000")) {
                var port = ctx.getEnvironment().getProperty("local.server.port");
                var client = RestClient.create("http://localhost:" + port);

                assertThat(client.get().uri("/api/health").retrieve().body(String.class)).isEqualTo("{\"status\":\"UP\"}");

                postgres.stop();

                assertThatThrownBy(() -> client.get().uri("/api/health").retrieve().body(String.class))
                        .isInstanceOfSatisfying(HttpServerErrorException.class,
                                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
            }
        }
    }
}
