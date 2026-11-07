package app.logis;

import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

/**
 * One Postgres for all tests of a JVM: a Testcontainers container by default, or an existing, empty database
 * when {@code LOGIS_TEST_DB_URL} is set (for environments without Docker; user and password from
 * {@code LOGIS_TEST_DB_USER} and {@code LOGIS_TEST_DB_PASSWORD}).
 */
public final class TestPostgres {

    private static final String EXTERNAL_URL = System.getenv("LOGIS_TEST_DB_URL");
    private static final PostgreSQLContainer<?> CONTAINER =
            EXTERNAL_URL == null ? new PostgreSQLContainer<>("postgres:17") : null;

    static {
        if (CONTAINER != null) {
            CONTAINER.start();
        }
    }

    private TestPostgres() {
    }

    public static String url() {
        return CONTAINER != null ? CONTAINER.getJdbcUrl() : EXTERNAL_URL;
    }

    public static String username() {
        return CONTAINER != null ? CONTAINER.getUsername() : System.getenv("LOGIS_TEST_DB_USER");
    }

    public static String password() {
        return CONTAINER != null ? CONTAINER.getPassword() : System.getenv("LOGIS_TEST_DB_PASSWORD");
    }

    /** As command-line arguments, so that they take precedence over application.yml. */
    public static List<String> args() {
        return List.of(
                "--spring.datasource.url=" + url(),
                "--spring.datasource.username=" + username(),
                "--spring.datasource.password=" + password());
    }
}
