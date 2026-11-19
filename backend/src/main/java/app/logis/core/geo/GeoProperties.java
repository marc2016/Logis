package app.logis.core.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * GeoIP database for sign-in locations (design D5 of sign-in-alerts).
 *
 * @param enabled     download and lookups; off: sign-ins have no location
 * @param directory   where the downloaded database is kept (a volume in the container)
 * @param downloadUrl monthly file, {@code %s} is replaced by {@code yyyy-MM}
 */
@ConfigurationProperties("logis.geoip")
record GeoProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("/var/lib/logis/geoip") Path directory,
        @DefaultValue("https://download.db-ip.com/free/dbip-city-lite-%s.mmdb.gz") String downloadUrl) {
}
