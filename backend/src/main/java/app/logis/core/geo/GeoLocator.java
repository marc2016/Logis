package app.logis.core.geo;

import java.util.Optional;

/** Location of a client address from a local database; never asks a third party (spec sign-in-alerts). */
public interface GeoLocator {

    /** Country code (ISO 3166), German country name and German city name; city may be null. */
    record Location(String countryCode, String countryName, String city) {

        /** „Hamburg, Deutschland“ or „Deutschland“. */
        public String label() {
            return city == null || city.isBlank() ? countryName : city + ", " + countryName;
        }
    }

    /** Empty for local and private addresses, unknown addresses and while no database is available. */
    Optional<Location> locate(String ip);
}
