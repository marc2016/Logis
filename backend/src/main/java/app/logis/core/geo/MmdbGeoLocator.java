package app.logis.core.geo;

import com.maxmind.db.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Reads the DB-IP Lite city database (design D4 of sign-in-alerts); the reader is swapped after a download. */
public class MmdbGeoLocator implements GeoLocator {

    static final String FILE_NAME = "dbip-city-lite.mmdb";

    private static final Logger log = LoggerFactory.getLogger(MmdbGeoLocator.class);
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final Path file;
    private final AtomicReference<Reader> reader = new AtomicReference<>();

    public MmdbGeoLocator(Path directory) {
        this.file = directory.resolve(FILE_NAME);
        reload();
    }

    Path file() {
        return file;
    }

    /** Opens the current file, if any, and closes the previous reader. */
    void reload() {
        Reader next = null;
        if (Files.isRegularFile(file)) {
            try {
                next = new Reader(file.toFile());
            } catch (IOException ex) {
                log.warn("GeoIP database {} cannot be read: {}", file, ex.toString());
            }
        }
        var previous = reader.getAndSet(next);
        if (previous != null) {
            try {
                previous.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    @Override
    public Optional<Location> locate(String ip) {
        var current = reader.get();
        if (current == null || ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return Optional.empty();
        }
        try {
            // A literal never triggers a DNS lookup.
            var address = InetAddress.getByName(ip);
            if (!isPublic(address)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> record = current.get(address, Map.class);
            return Optional.ofNullable(record).flatMap(MmdbGeoLocator::toLocation);
        } catch (IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Location> toLocation(Map<String, Object> record) {
        if (!(record.get("country") instanceof Map<?, ?> country) || !(country.get("iso_code") instanceof String code)) {
            return Optional.empty();
        }
        var countryName = name((Map<String, Object>) country).orElse(code);
        String city = null;
        if (record.get("city") instanceof Map<?, ?> cityRecord) {
            city = name((Map<String, Object>) cityRecord).orElse(null);
        }
        return Optional.of(new Location(code, countryName, city));
    }

    private static Optional<String> name(Map<String, Object> record) {
        if (record.get("names") instanceof Map<?, ?> names) {
            var de = names.get("de");
            var en = names.get("en");
            return Optional.ofNullable(de instanceof String s ? s : en instanceof String s ? s : null);
        }
        return Optional.empty();
    }

    /** Local, private, CGNAT and benchmarking addresses have no location. */
    static boolean isPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        var b = address.getAddress();
        if (address instanceof Inet4Address) {
            var first = b[0] & 0xff;
            var second = b[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            return !(first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) != 0xfc;
        }
        return true;
    }
}
