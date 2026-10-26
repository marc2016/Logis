package app.logis.core.sharing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface ObjectShareRepository extends JpaRepository<ObjectShare, Long> {

    @Query("select s from ObjectShare s where s.objectType = :type and s.objectId = :objectId and s.userId = :userId")
    Optional<ObjectShare> find(String type, Long objectId, Long userId);

    @Query("select s from ObjectShare s where s.objectType = :type and s.objectId = :objectId order by s.id")
    List<ObjectShare> findAllForObject(String type, Long objectId);

    @Query("select s from ObjectShare s where s.objectType = :type and s.userId = :userId")
    List<ObjectShare> findAllForUser(String type, Long userId);

    /** Sperrt die Owner-Zeilen eines Objekts fuer die Invariante "mindestens ein Owner". */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ObjectShare s where s.objectType = :type and s.objectId = :objectId "
            + "and s.role = app.logis.core.sharing.ShareRole.OWNER")
    List<ObjectShare> lockOwners(String type, Long objectId);

    @Modifying
    @Query("delete from ObjectShare s where s.objectType = :type and s.objectId = :objectId")
    void deleteAllForObject(String type, Long objectId);
}
