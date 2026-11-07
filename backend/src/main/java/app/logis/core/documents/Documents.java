package app.logis.core.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Set;

/**
 * Public API for owners: removes documents together with their owner.
 */
@Service
@Transactional
public class Documents {

    /** Types the server may store itself; uploads by users stay limited to PDF and images. */
    static final Set<String> SERVER_TYPES = Set.of("message/rfc822", "application/pdf", "text/plain");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    Documents(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Stores a file the server created itself (e.g. an imported mail) without the upload checks. The caller has
     * already checked the rights on the owner.
     */
    public Long store(String ownerType, Long ownerId, String fileName, String contentType, byte[] content) {
        if (!SERVER_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported server document type: " + contentType);
        }
        if (content.length == 0 || content.length > 25L * 1024 * 1024) {
            throw new IllegalArgumentException("Server document empty or too large: " + content.length);
        }
        return jdbc.queryForObject("INSERT INTO core.documents "
                        + "(owner_type, owner_id, file_name, content_type, size_bytes, content, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                ownerType, ownerId, DocumentService.fileName(fileName), contentType, content.length, content,
                Timestamp.from(clock.instant()));
    }

    /** Deletes all documents of the owner; call it in the same transaction that deletes the owner. */
    public void deleteAll(String ownerType, Long ownerId) {
        jdbc.update("DELETE FROM core.documents WHERE owner_type = ? AND owner_id = ?", ownerType, ownerId);
    }

    /** Number of documents of the owner. */
    @Transactional(readOnly = true)
    public int count(String ownerType, Long ownerId) {
        return jdbc.queryForObject("SELECT count(*) FROM core.documents WHERE owner_type = ? AND owner_id = ?",
                Integer.class, ownerType, ownerId);
    }
}
