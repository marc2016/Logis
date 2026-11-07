package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("select count(b) > 0 from Booking b where b.unitId = :unitId and not b.cancelled "
            + "and b.arrival < :departure and b.departure > :arrival and (:excludeId is null or b.id <> :excludeId)")
    boolean overlaps(Long unitId, LocalDate arrival, LocalDate departure, Long excludeId);

    @Query("select count(b) > 0 from Booking b where b.unitId = :unitId and lower(b.externalRef) = lower(:ref) "
            + "and (:excludeId is null or b.id <> :excludeId)")
    boolean refTaken(Long unitId, String ref, Long excludeId);

    @Query("select b from Booking b where b.unitId in :unitIds "
            + "and (cast(:from as LocalDate) is null or b.departure > :from) "
            + "and (cast(:to as LocalDate) is null or b.arrival <= :to)")
    List<Booking> findForUnits(Collection<Long> unitIds, LocalDate from, LocalDate to);

    boolean existsByUnitIdIn(Collection<Long> unitIds);
}
