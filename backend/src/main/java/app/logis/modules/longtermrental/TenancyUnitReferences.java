package app.logis.modules.longtermrental;

import app.logis.core.realestate.UnitReferences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Blocks deleting a property whose units still have tenancies. Deliberately active even when the module is
 * disabled: its data is kept then and must not lose its unit.
 */
@Component
class TenancyUnitReferences implements UnitReferences {

    static final String BLOCKED = "Das Objekt hat noch Mietverhältnisse";

    private final JdbcTemplate jdbc;

    TenancyUnitReferences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String blockingReason(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return null;
        }
        var exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM longtermrental.tenancies WHERE unit_id = ANY(?))",
                Boolean.class, (Object) unitIds.toArray(Long[]::new));
        return Boolean.TRUE.equals(exists) ? BLOCKED : null;
    }
}
