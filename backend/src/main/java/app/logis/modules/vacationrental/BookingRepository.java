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
            + "and lower(coalesce(b.agency, '')) = lower(coalesce(:agency, '')) "
            + "and (:excludeId is null or b.id <> :excludeId)")
    boolean refTaken(Long unitId, String agency, String ref, Long excludeId);

    /** Bookings with this number on the given units: of this agency, or without agency (entered by hand). */
    @Query("select b from Booking b where b.unitId in :unitIds and lower(b.externalRef) = lower(:ref) "
            + "and (b.agency is null or lower(b.agency) = lower(:agency)) order by b.agency nulls last, b.id")
    List<Booking> findByRef(Collection<Long> unitIds, String agency, String ref);

    @Query("select b from Booking b where b.unitId in :unitIds "
            + "and (cast(:from as LocalDate) is null or b.departure > :from) "
            + "and (cast(:to as LocalDate) is null or b.arrival <= :to)")
    List<Booking> findForUnits(Collection<Long> unitIds, LocalDate from, LocalDate to);

    boolean existsByUnitIdIn(Collection<Long> unitIds);
}
