package app.logis.core.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Oeffentlicher Health-Endpunkt fuer nginx und Monitoring. Liefert bewusst keine Details.
 */
@RestController
class HealthController {

    private final JdbcTemplate jdbc;

    HealthController(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(2);
    }

    @GetMapping("/api/health")
    ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
