package app.logis;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/** One Postgres container for all tests of a JVM. */
public final class TestPostgres {

    public static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:17");

    static {
        CONTAINER.start();
    }

    private TestPostgres() {
    }

    /** As command-line arguments, so that they take precedence over application.yml. */
    public static List<String> args() {
        return List.of(
                "--spring.datasource.url=" + CONTAINER.getJdbcUrl(),
                "--spring.datasource.username=" + CONTAINER.getUsername(),
                "--spring.datasource.password=" + CONTAINER.getPassword());
    }
}
