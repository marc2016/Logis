package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface UnitMappingRepository extends JpaRepository<UnitMapping, Long> {

    @Query("select m from UnitMapping m where m.ownerUserId = :ownerUserId and lower(m.agency) = lower(:agency) "
            + "and lower(m.propertyName) = lower(:propertyName)")
    Optional<UnitMapping> find(Long ownerUserId, String agency, String propertyName);
}
