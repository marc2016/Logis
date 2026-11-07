package app.logis.core.documents;

import app.logis.core.web.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stores documents in {@code core.documents}; access is decided by the owner type. */
@Service
@Transactional
class DocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final String INVALID_FILE = "Nur PDF- oder Bilddateien bis 10 MB";
    private static final String NOT_FOUND = "Dokument nicht gefunden";

    private final JdbcTemplate jdbc;
    private final Map<String, DocumentOwnerType> ownerTypes;
    private final Clock clock;

    DocumentService(JdbcTemplate jdbc, List<DocumentOwnerType> ownerTypes, Clock clock) {
        this.jdbc = jdbc;
        this.ownerTypes = ownerTypes.stream().collect(Collectors.toMap(DocumentOwnerType::type, Function.identity()));
        this.clock = clock;
    }

    record Metadata(Long id, String fileName, String contentType, long sizeBytes, Instant createdAt) {
    }

    record Download(String fileName, String contentType, byte[] content) {
    }

    private record Owner(String type, Long id) {
    }

    @Transactional(readOnly = true)
    List<Metadata> list(Long userId, String ownerType, Long ownerId) {
        requireRead(userId, ownerType, ownerId);
        return jdbc.query("SELECT id, file_name, content_type, size_bytes, created_at FROM core.documents "
                        + "WHERE owner_type = ? AND owner_id = ? ORDER BY created_at, id",
                (rs, i) -> new Metadata(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getTimestamp(5).toInstant()),
                ownerType, ownerId);
    }

    Metadata upload(Long userId, String ownerType, Long ownerId, String originalName, byte[] content) {
        requireWrite(userId, ownerType, ownerId);
        if (content.length == 0 || content.length > MAX_BYTES) {
            throw ApiException.badRequest(INVALID_FILE);
        }
        var contentType = FileType.detect(content).orElseThrow(() -> ApiException.badRequest(INVALID_FILE));
        var fileName = fileName(originalName);
        var now = clock.instant();
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO core.documents "
                    + "(owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, ownerType);
            ps.setLong(2, ownerId);
            ps.setString(3, fileName);
            ps.setString(4, contentType);
            ps.setInt(5, content.length);
            ps.setBytes(6, content);
            ps.setTimestamp(7, Timestamp.from(now));
            return ps;
        }, keys);
        var id = ((Number) keys.getKeys().get("id")).longValue();
        return new Metadata(id, fileName, contentType, content.length, now);
    }

    @Transactional(readOnly = true)
    Download download(Long userId, Long documentId) {
        var owner = owner(documentId);
        requireRead(userId, owner.type(), owner.id());
        return jdbc.queryForObject("SELECT file_name, content_type, content FROM core.documents WHERE id = ?",
                (rs, i) -> new Download(rs.getString(1), rs.getString(2), rs.getBytes(3)), documentId);
    }

    void delete(Long userId, Long documentId) {
        var owner = owner(documentId);
        requireWrite(userId, owner.type(), owner.id());
        jdbc.update("DELETE FROM core.documents WHERE id = ?", documentId);
    }

    private Owner owner(Long documentId) {
        return jdbc.query("SELECT owner_type, owner_id FROM core.documents WHERE id = ?",
                        (rs, i) -> new Owner(rs.getString(1), rs.getLong(2)), documentId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private DocumentOwnerType requireRead(Long userId, String ownerType, Long ownerId) {
        var type = ownerType == null ? null : ownerTypes.get(ownerType);
        if (type == null || ownerId == null || !type.canRead(ownerId, userId)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return type;
    }

    private void requireWrite(Long userId, String ownerType, Long ownerId) {
        if (!requireRead(userId, ownerType, ownerId).canWrite(ownerId, userId)) {
            throw ApiException.forbidden("Dafür fehlt die Berechtigung.");
        }
    }

    /** Keeps only the last path segment, without control characters, at most 255 characters. */
    static String fileName(String original) {
        var name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            name = "Dokument";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
