package app.logis.core.dashboard;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * The signed-in user's start page tiles (spec dashboard; design D1 of configurable-dashboard). Only the shape is
 * checked: the tile catalog belongs to the frontend modules, and tiles of inactive modules must survive.
 */
@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    static final int MAX_TILES = 40;
    static final int MAX_ID = 36;
    static final int MAX_SETTINGS_BYTES = 2048;
    private static final Pattern TYPE = Pattern.compile("^[a-z]+(\\.[a-z0-9-]+)+$");
    static final String INVALID = "Die Übersicht konnte nicht gespeichert werden: ungültige Kacheln.";

    /** {@code tiles} is {@code null} while the user has not customised the start page. */
    record Dashboard(JsonNode tiles) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    DashboardController(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @GetMapping
    Dashboard get(@AuthenticationPrincipal LogisPrincipal me) {
        var rows = jdbc.queryForList("SELECT tiles::text FROM core.dashboards WHERE user_id = ?", String.class,
                me.userId());
        if (rows.isEmpty()) {
            return new Dashboard(null);
        }
        try {
            return new Dashboard(json.readTree(rows.getFirst()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Saves the tiles; {@code null} goes back to the default. */
    @PutMapping
    @Transactional
    Dashboard put(@RequestBody Dashboard body, @AuthenticationPrincipal LogisPrincipal me) {
        var tiles = body == null ? null : body.tiles();
        if (tiles == null || tiles.isNull()) {
            jdbc.update("DELETE FROM core.dashboards WHERE user_id = ?", me.userId());
            return new Dashboard(null);
        }
        validate(tiles);
        jdbc.update("INSERT INTO core.dashboards (user_id, tiles, updated_at) VALUES (?, ?::jsonb, ?) "
                        + "ON CONFLICT (user_id) DO UPDATE SET tiles = EXCLUDED.tiles, updated_at = EXCLUDED.updated_at",
                me.userId(), tiles.toString(), Timestamp.from(clock.instant()));
        return new Dashboard(tiles);
    }

    private static void validate(JsonNode tiles) {
        if (!tiles.isArray() || tiles.size() > MAX_TILES) {
            throw ApiException.badRequest(INVALID);
        }
        var ids = new HashSet<String>();
        for (var tile : tiles) {
            var id = tile.get("id");
            var type = tile.get("type");
            var settings = tile.get("settings");
            if (!tile.isObject() || tile.size() != 3 || id == null || !id.isTextual() || id.asText().isEmpty()
                    || id.asText().length() > MAX_ID || !ids.add(id.asText())
                    || type == null || !type.isTextual() || !TYPE.matcher(type.asText()).matches()
                    || settings == null || !settings.isObject()
                    || settings.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SETTINGS_BYTES) {
                throw ApiException.badRequest(INVALID);
            }
        }
    }
}
