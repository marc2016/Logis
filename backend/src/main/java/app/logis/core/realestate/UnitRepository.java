package app.logis.core.realestate;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface UnitRepository extends JpaRepository<Unit, Long> {

    @Query("select u from Unit u where u.propertyId = :propertyId order by u.sortOrder, u.id")
    List<Unit> findByProperty(Long propertyId);

    @Query("select u from Unit u where u.propertyId in :propertyIds order by u.sortOrder, u.id")
    List<Unit> findByProperties(Collection<Long> propertyIds);

    @Query("select u.id from Unit u where u.propertyId = :propertyId")
    List<Long> findIdsByProperty(Long propertyId);

    /** Locks a property's units for the invariant "at least one active unit". */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Unit u where u.propertyId = :propertyId")
    List<Unit> lockByProperty(Long propertyId);

    @Query("select count(u) > 0 from Unit u where u.propertyId = :propertyId and lower(u.name) = lower(:name) "
            + "and (:excludeId is null or u.id <> :excludeId)")
    boolean nameTaken(Long propertyId, String name, Long excludeId);
}
