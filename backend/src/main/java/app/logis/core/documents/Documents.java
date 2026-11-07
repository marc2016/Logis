package app.logis.core.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API for owners: removes documents together with their owner.
 */
@Service
@Transactional
public class Documents {

    private final JdbcTemplate jdbc;

    Documents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
