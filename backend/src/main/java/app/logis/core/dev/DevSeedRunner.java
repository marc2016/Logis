package app.logis.core.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Development only: fills an empty database with test accounts and data from {@code dev/seed.sql}. Runs before the
 * initial admin and the other startup tasks, so they see the data. The script itself skips a database that already
 * holds the development data.
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final JdbcTemplate jdbc;
    private final String seedFile;

    DevSeedRunner(JdbcTemplate jdbc, @Value("${logis.dev.seed-file:}") String seedFile) {
        this.jdbc = jdbc;
        this.seedFile = seedFile;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (seedFile.isBlank()) {
            return;
        }
        var path = Path.of(seedFile);
        if (!Files.isRegularFile(path)) {
            log.warn("Development seed {} not found, skipping", path.toAbsolutePath());
            return;
        }
        var users = jdbc.queryForObject("SELECT count(*) FROM core.users WHERE lower(username) <> 'admin'", Long.class);
        if (users != null && users > 0) {
            return;
        }
        log.info("Filling the empty development database from {}", path);
        jdbc.execute(Files.readString(path, StandardCharsets.UTF_8));
    }
}
