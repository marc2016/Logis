package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface AgencyRepository extends JpaRepository<Agency, Long> {

    @Query("select a from Agency a where lower(a.name) = lower(:name)")
    Optional<Agency> findByNameIgnoreCase(String name);

    /** The agency that has this mail name, regardless of case. */
    @Query("select a from Agency a where a.id = (select al.agencyId from AgencyAlias al "
            + "where lower(al.alias) = lower(:alias))")
    Optional<Agency> findByAlias(String alias);
}
