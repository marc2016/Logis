package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface AgencyAliasRepository extends JpaRepository<AgencyAlias, Long> {

    @Query("select al from AgencyAlias al where lower(al.alias) = lower(:alias)")
    Optional<AgencyAlias> findByAliasIgnoreCase(String alias);

    List<AgencyAlias> findByAgencyIdIn(Collection<Long> agencyIds);

    @Modifying
    @Query("update AgencyAlias al set al.agencyId = :targetId where al.agencyId = :sourceId")
    void moveAll(Long sourceId, Long targetId);
}
