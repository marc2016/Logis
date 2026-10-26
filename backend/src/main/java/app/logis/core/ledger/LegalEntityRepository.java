package app.logis.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {

    @Query("select e from LegalEntity e where e.managerUserId = :userId order by e.personal desc, lower(e.name)")
    List<LegalEntity> findManagedBy(Long userId);

    @Query("select e from LegalEntity e where e.id = :id and e.managerUserId = :userId")
    Optional<LegalEntity> findManaged(Long id, Long userId);

    @Query("select count(e) > 0 from LegalEntity e where e.managerUserId = :userId and lower(e.name) = lower(:name) "
            + "and (:excludeId is null or e.id <> :excludeId)")
    boolean nameTaken(Long userId, String name, Long excludeId);
}
