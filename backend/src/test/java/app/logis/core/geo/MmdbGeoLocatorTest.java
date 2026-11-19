package app.logis.core.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Lookups in a test database with documentation networks only (design D4 of sign-in-alerts). */
class MmdbGeoLocatorTest {

    @TempDir
    static Path dir;

    static MmdbGeoLocator locator() throws Exception {
        new MmdbTestWriter()
                .network("203.0.113.0/24", "DE", "Deutschland", "Hamburg")
                .network("198.51.100.0/24", "ES", "Spanien", "Madrid")
                .network("192.0.2.0/25", "BR", "Brasilien", null)
                .write(dir.resolve(MmdbGeoLocator.FILE_NAME));
        return new MmdbGeoLocator(dir);
    }

    @Test
    void knownNetworks() throws Exception {
        var locator = locator();
        assertThat(locator.locate("203.0.113.7")).contains(new GeoLocator.Location("DE", "Deutschland", "Hamburg"));
        assertThat(locator.locate("198.51.100.200")).contains(new GeoLocator.Location("ES", "Spanien", "Madrid"));
        var brazil = locator.locate("192.0.2.1").orElseThrow();
        assertThat(brazil.city()).isNull();
        assertThat(brazil.label()).isEqualTo("Brasilien");
        assertThat(locator.locate("203.0.113.7").orElseThrow().label()).isEqualTo("Hamburg, Deutschland");
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.20", "10.0.0.1", "172.16.5.4", "127.0.0.1", "169.254.1.1", "100.64.0.1",
            "198.18.0.1", "198.19.255.1", "::1", "fd00::1", "fe80::1", "192.0.2.200", "8.8.8.8", "kein-ip",
            "", "example.org"})
    void noLocation(String ip) throws Exception {
        assertThat(locator().locate(ip)).isEmpty();
    }

    @Test
    void missingFile(@TempDir Path empty) {
        assertThat(new MmdbGeoLocator(empty).locate("203.0.113.7")).isEmpty();
    }
}
