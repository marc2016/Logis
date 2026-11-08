package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface UnitMappingRepository extends JpaRepository<UnitMapping, Long> {

    @Query("select m from UnitMapping m where m.ownerUserId = :ownerUserId and m.agencyId = :agencyId "
            + "and lower(m.propertyName) = lower(:propertyName)")
    Optional<UnitMapping> find(Long ownerUserId, Long agencyId, String propertyName);

    List<UnitMapping> findByAgencyId(Long agencyId);

    boolean existsByAgencyId(Long agencyId);
}
