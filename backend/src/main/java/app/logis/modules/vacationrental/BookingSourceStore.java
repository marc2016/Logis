package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Sources of bookings and the locations of field values in them (design.md D2). */
@Repository
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingSourceStore {

    enum Scope {
        CURRENT, PROPOSAL
    }

    record Source(Long id, Long bookingId, Long documentId, String kind, String label, String sender,
            Instant receivedAt, String subject, String text) {
    }

    record FieldLocation(BookingField field, Long sourceId, Location location, String sourceValue) {
    }

    private static final String SOURCE_COLUMNS =
            "id, booking_id, document_id, kind, label, sender, received_at, subject, text";

    private final JdbcTemplate jdbc;

    BookingSourceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Long insert(Long bookingId, Long documentId, String kind, String label, String sender, Instant receivedAt,
            String subject, String text) {
        return jdbc.queryForObject("INSERT INTO vacationrental.booking_sources "
                        + "(booking_id, document_id, kind, label, sender, received_at, subject, text) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, bookingId, documentId, kind,
                label, sender, receivedAt == null ? null : Timestamp.from(receivedAt), subject, text);
    }

    /** The booking's sources, oldest first. */
    List<Source> list(Long bookingId) {
        return jdbc.query("SELECT " + SOURCE_COLUMNS + " FROM vacationrental.booking_sources WHERE booking_id = ? "
                + "ORDER BY coalesce(received_at, created_at), id", BookingSourceStore::source, bookingId);
    }

    Optional<Source> get(Long bookingId, Long sourceId) {
        return jdbc.query("SELECT " + SOURCE_COLUMNS + " FROM vacationrental.booking_sources "
                + "WHERE booking_id = ? AND id = ?", BookingSourceStore::source, bookingId, sourceId).stream()
                .findFirst();
    }

    boolean hasSources(Long bookingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM vacationrental.booking_sources "
                + "WHERE booking_id = ?)", Boolean.class, bookingId));
    }

    void deleteByDocument(Long documentId) {
        jdbc.update("DELETE FROM vacationrental.booking_sources WHERE document_id = ?", documentId);
    }

    Map<BookingField, FieldLocation> locations(Long bookingId, Scope scope) {
        var result = new EnumMap<BookingField, FieldLocation>(BookingField.class);
        jdbc.query("SELECT field, source_id, part, start_pos, end_pos, source_value "
                + "FROM vacationrental.booking_field_sources WHERE booking_id = ? AND scope = ?", rs -> {
                    var field = BookingField.ofKey(rs.getString("field"));
                    if (field.isPresent()) {
                        result.put(field.get(), new FieldLocation(field.get(), rs.getLong("source_id"),
                                new Location(Location.Part.valueOf(rs.getString("part")), rs.getInt("start_pos"),
                                        rs.getInt("end_pos")), rs.getString("source_value")));
                    }
                }, bookingId, scope.name());
        return result;
    }

    /** Sets the location of a field, replacing an existing one. */
    void put(Long bookingId, Scope scope, BookingField field, Long sourceId, Location location, String value) {
        jdbc.update("INSERT INTO vacationrental.booking_field_sources "
                + "(booking_id, scope, field, source_id, part, start_pos, end_pos, source_value) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (booking_id, scope, field) DO UPDATE SET "
                + "source_id = excluded.source_id, part = excluded.part, start_pos = excluded.start_pos, "
                + "end_pos = excluded.end_pos, source_value = excluded.source_value", bookingId, scope.name(),
                field.key(), sourceId, location.part().name(), location.start(), location.end(), value);
    }

    /** Sets the location of a field only if it has none. */
    void putIfAbsent(Long bookingId, Scope scope, BookingField field, Long sourceId, Location location,
            String value) {
        jdbc.update("INSERT INTO vacationrental.booking_field_sources "
                + "(booking_id, scope, field, source_id, part, start_pos, end_pos, source_value) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (booking_id, scope, field) DO NOTHING", bookingId,
                scope.name(), field.key(), sourceId, location.part().name(), location.start(), location.end(), value);
    }

    void deleteScope(Long bookingId, Scope scope) {
        jdbc.update("DELETE FROM vacationrental.booking_field_sources WHERE booking_id = ? AND scope = ?", bookingId,
                scope.name());
    }

    /** Removes the locations of these fields in both scopes. */
    void deleteFields(Long bookingId, Collection<BookingField> fields) {
        for (var field : fields) {
            jdbc.update("DELETE FROM vacationrental.booking_field_sources WHERE booking_id = ? AND field = ?",
                    bookingId, field.key());
        }
    }

    /** The proposal's locations of these fields become the current ones; all other proposal locations go. */
    void promote(Long bookingId, Collection<BookingField> fields) {
        for (var field : fields) {
            var moved = jdbc.update("UPDATE vacationrental.booking_field_sources SET scope = 'CURRENT' "
                    + "WHERE booking_id = ? AND scope = 'PROPOSAL' AND field = ? AND NOT EXISTS ("
                    + "SELECT 1 FROM vacationrental.booking_field_sources c WHERE c.booking_id = ? "
                    + "AND c.scope = 'CURRENT' AND c.field = ?)", bookingId, field.key(), bookingId, field.key());
            if (moved == 0) {
                jdbc.update("UPDATE vacationrental.booking_field_sources c SET source_id = p.source_id, "
                        + "part = p.part, start_pos = p.start_pos, end_pos = p.end_pos, source_value = p.source_value "
                        + "FROM vacationrental.booking_field_sources p WHERE c.booking_id = ? AND c.scope = 'CURRENT' "
                        + "AND c.field = ? AND p.booking_id = c.booking_id AND p.scope = 'PROPOSAL' "
                        + "AND p.field = c.field", bookingId, field.key());
            }
        }
        deleteScope(bookingId, Scope.PROPOSAL);
    }

    private static Source source(ResultSet rs, int row) throws SQLException {
        var received = rs.getTimestamp("received_at");
        return new Source(rs.getLong("id"), rs.getLong("booking_id"), rs.getLong("document_id"), rs.getString("kind"),
                rs.getString("label"), rs.getString("sender"), received == null ? null : received.toInstant(),
                rs.getString("subject"), rs.getString("text"));
    }
}
