package app.logis;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/** Ein Postgres-Container fuer alle Tests einer JVM. */
public final class TestPostgres {

    public static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:17");

    static {
        CONTAINER.start();
    }

    private TestPostgres() {
    }

    /** Als Kommandozeilenargumente, damit sie Vorrang vor application.yml haben. */
    public static List<String> args() {
        return List.of(
                "--spring.datasource.url=" + CONTAINER.getJdbcUrl(),
                "--spring.datasource.username=" + CONTAINER.getUsername(),
                "--spring.datasource.password=" + CONTAINER.getPassword());
    }
}
