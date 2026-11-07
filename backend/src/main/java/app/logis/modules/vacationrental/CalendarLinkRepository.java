package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CalendarLinkRepository extends JpaRepository<CalendarLink, Long> {

    Optional<CalendarLink> findByTokenHash(String tokenHash);

    List<CalendarLink> findByUnitIdAndCreatedByOrderByCreatedAt(Long unitId, Long createdBy);
}
