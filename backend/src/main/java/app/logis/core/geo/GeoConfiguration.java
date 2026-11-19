package app.logis.core.geo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeoProperties.class)
class GeoConfiguration {

    /** Without the database switched on, no sign-in has a location. */
    @Bean
    GeoLocator geoLocator(GeoProperties properties) {
        return properties.enabled() ? new MmdbGeoLocator(properties.directory()) : ip -> Optional.empty();
    }
}
