package app.logis.core.preferences;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The signed-in user's settings (spec user-preferences; design D1 of booking-compact-view). Keys name their module,
 * values are small JSON values; the meaning of a value belongs to the part of the interface that uses it.
 */
@RestController
@RequestMapping("/api/preferences")
class PreferencesController {

    static final int MAX_KEY = 100;
    static final int MAX_VALUE_BYTES = 4096;
    static final int MAX_KEYS = 100;
    static final String INVALID = "Die Einstellung ist ungültig.";
    private static final Pattern KEY = Pattern.compile("^[a-z]+(\\.[A-Za-z0-9-]+)+$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    PreferencesController(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @GetMapping
    Map<String, JsonNode> all(@AuthenticationPrincipal LogisPrincipal me) {
        var result = new LinkedHashMap<String, JsonNode>();
        jdbc.query("SELECT key, value::text FROM core.user_preferences WHERE user_id = ? ORDER BY key", rs -> {
            try {
                result.put(rs.getString(1), json.readTree(rs.getString(2)));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(ex);
            }
        }, me.userId());
        return result;
    }

    @PutMapping("/{key}")
    @Transactional
    ResponseEntity<Void> put(@PathVariable String key, @RequestBody(required = false) JsonNode value,
            @AuthenticationPrincipal LogisPrincipal me) {
        if (!validKey(key) || value == null || value.isMissingNode()
                || value.toString().getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw ApiException.badRequest(INVALID);
        }
        // Lock the user's rows, so that two new keys at the limit cannot both pass the count.
        jdbc.queryForList("SELECT key FROM core.user_preferences WHERE user_id = ? FOR UPDATE", String.class, me.userId());
        var exists = jdbc.queryForObject("SELECT count(*) FROM core.user_preferences WHERE user_id = ? AND key = ?",
                Integer.class, me.userId(), key) > 0;
        var count = jdbc.queryForObject("SELECT count(*) FROM core.user_preferences WHERE user_id = ?", Integer.class,
                me.userId());
        if (!exists && count >= MAX_KEYS) {
            throw ApiException.badRequest(INVALID);
        }
        jdbc.update("INSERT INTO core.user_preferences (user_id, key, value, updated_at) VALUES (?, ?, ?::jsonb, ?) "
                        + "ON CONFLICT (user_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at",
                me.userId(), key, value.toString(), Timestamp.from(clock.instant()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{key}")
    ResponseEntity<Void> delete(@PathVariable String key, @AuthenticationPrincipal LogisPrincipal me) {
        if (!validKey(key)) {
            throw ApiException.badRequest(INVALID);
        }
        jdbc.update("DELETE FROM core.user_preferences WHERE user_id = ? AND key = ?", me.userId(), key);
        return ResponseEntity.noContent().build();
    }

    private static boolean validKey(String key) {
        return key != null && key.length() <= MAX_KEY && KEY.matcher(key).matches();
    }
}
