package app.logis.core.geo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Download of the GeoIP database against a local HTTP server (design D5 of sign-in-alerts). */
@ExtendWith(OutputCaptureExtension.class)
class GeoIpUpdaterTest {

    private static final Clock NOVEMBER = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            var body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static byte[] gzipped(MmdbTestWriter writer) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(out)) {
            gz.write(writer.bytes());
        }
        return out.toByteArray();
    }

    private GeoIpUpdater updater(boolean enabled, MmdbGeoLocator locator) {
        var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/dbip-city-lite-%s.mmdb.gz";
        return new GeoIpUpdater(new GeoProperties(enabled, dir, url), locator, new SyncTaskExecutor(), NOVEMBER);
    }

    @Test
    void downloadsAndSwapsTheReader() throws Exception {
        files.put("/dbip-city-lite-2026-11.mmdb.gz",
                gzipped(new MmdbTestWriter().network("203.0.113.0/24", "DE", "Deutschland", "Hamburg")));
        var locator = new MmdbGeoLocator(dir);
        assertThat(locator.locate("203.0.113.7")).isEmpty();

        assertThat(updater(true, locator).updateIfOutdated()).isTrue();

        assertThat(locator.locate("203.0.113.7")).map(GeoLocator.Location::countryCode).contains("DE");
        assertThat(Files.exists(dir.resolve("dbip-city-lite.mmdb.tmp"))).isFalse();
    }

    @Test
    void fallsBackToThePreviousMonth() throws Exception {
        files.put("/dbip-city-lite-2026-10.mmdb.gz",
                gzipped(new MmdbTestWriter().network("198.51.100.0/24", "ES", "Spanien", "Madrid")));
        var locator = new MmdbGeoLocator(dir);
        assertThat(updater(true, locator).updateIfOutdated()).isTrue();
        assertThat(locator.locate("198.51.100.1")).map(GeoLocator.Location::countryCode).contains("ES");
    }

    @Test
    void failureKeepsTheOldFile(CapturedOutput output) throws Exception {
        new MmdbTestWriter().network("203.0.113.0/24", "DE", "Deutschland", "Hamburg")
                .write(dir.resolve(MmdbGeoLocator.FILE_NAME));
        Files.setLastModifiedTime(dir.resolve(MmdbGeoLocator.FILE_NAME), FileTime.from(Instant.parse("2026-09-01T00:00:00Z")));
        var locator = new MmdbGeoLocator(dir);
        // Neither month exists, and a broken file must not replace the old one either.
        assertThat(updater(true, locator).updateIfOutdated()).isFalse();
        // A cut-off gzip stream.
        files.put("/dbip-city-lite-2026-11.mmdb.gz", new byte[] {31, -117, 8, 0, 0, 0, 0, 0});
        assertThat(updater(true, locator).updateIfOutdated()).isFalse();

        assertThat(locator.locate("203.0.113.7")).map(GeoLocator.Location::countryCode).contains("DE");
        assertThat(output.getAll()).contains("GeoIP database could not be downloaded");
    }

    @Test
    void currentFileIsNotDownloadedAgain() throws Exception {
        new MmdbTestWriter().network("203.0.113.0/24", "DE", "Deutschland", "Hamburg")
                .write(dir.resolve(MmdbGeoLocator.FILE_NAME));
        Files.setLastModifiedTime(dir.resolve(MmdbGeoLocator.FILE_NAME), FileTime.from(Instant.parse("2026-11-03T05:00:00Z")));
        assertThat(updater(true, new MmdbGeoLocator(dir)).updateIfOutdated()).isFalse();
        assertThat(requests.get()).isZero();
    }

    @Test
    void disabledDownloadsNothing() {
        var updater = updater(false, new MmdbGeoLocator(dir));
        updater.atStartup();
        updater.monthly();
        assertThat(requests.get()).isZero();
    }
}
