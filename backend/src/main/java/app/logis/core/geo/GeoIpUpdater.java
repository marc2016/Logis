package app.logis.core.geo;

import app.logis.shared.GermanTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.zip.GZIPInputStream;

/**
 * Keeps the DB-IP Lite city database current: at startup if missing or outdated, then on the 3rd of each month
 * (design D5 of sign-in-alerts). Failures keep the previous file.
 */
@Component
class GeoIpUpdater {

    private static final Logger log = LoggerFactory.getLogger(GeoIpUpdater.class);
    private static final ZoneId ZONE = GermanTime.ZONE;

    private final GeoProperties properties;
    private final GeoLocator locator;
    private final TaskExecutor executor;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .build();

    GeoIpUpdater(GeoProperties properties, GeoLocator locator,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock) {
        this.properties = properties;
        this.locator = locator;
        this.executor = executor;
        this.clock = clock;
    }

    /** In the background, so that startup is not delayed by 130 MB. */
    @EventListener(ApplicationReadyEvent.class)
    void atStartup() {
        if (properties.enabled()) {
            executor.execute(this::updateIfOutdated);
        }
    }

    @Scheduled(cron = "0 41 4 3 * *", zone = "Europe/Berlin")
    void monthly() {
        if (properties.enabled()) {
            updateIfOutdated();
        }
    }

    /** Returns whether a new file was installed. */
    synchronized boolean updateIfOutdated() {
        if (!(locator instanceof MmdbGeoLocator mmdb)) {
            return false;
        }
        var file = mmdb.file();
        var month = YearMonth.now(clock.withZone(ZONE));
        try {
            if (Files.isRegularFile(file)
                    && !YearMonth.from(Files.getLastModifiedTime(file).toInstant().atZone(ZONE)).isBefore(month)) {
                return false;
            }
            Files.createDirectories(file.getParent());
            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            if (!download(month, temp) && !download(month.minusMonths(1), temp)) {
                log.warn("GeoIP database could not be downloaded: no file for {} or the month before", month);
                return false;
            }
            // Opening it once proves it is a readable database before it replaces the old one.
            try (var check = new com.maxmind.db.Reader(temp.toFile())) {
                check.getMetadata();
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            mmdb.reload();
            log.info("GeoIP database updated");
            return true;
        } catch (IOException | RuntimeException ex) {
            log.warn("GeoIP database could not be downloaded: {}", ex.toString());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** False if the month's file does not exist (404); throws for other failures. */
    private boolean download(YearMonth month, java.nio.file.Path target) throws IOException, InterruptedException {
        var uri = URI.create(properties.downloadUrl().formatted(month));
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            try (var in = new GZIPInputStream(body)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }
}
