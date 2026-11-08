package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("select count(b) > 0 from Booking b where b.unitId = :unitId and not b.cancelled "
            + "and b.arrival < :departure and b.departure > :arrival and (:excludeId is null or b.id <> :excludeId)")
    boolean overlaps(Long unitId, LocalDate arrival, LocalDate departure, Long excludeId);

    @Query("select count(b) > 0 from Booking b where b.unitId = :unitId and lower(b.externalRef) = lower(:ref) "
            + "and coalesce(b.agencyId, 0) = coalesce(:agencyId, 0) "
            + "and (:excludeId is null or b.id <> :excludeId)")
    boolean refTaken(Long unitId, Long agencyId, String ref, Long excludeId);

    /** Bookings with this number on the given units: of this agency, or without agency (entered by hand). */
    @Query("select b from Booking b where b.unitId in :unitIds and lower(b.externalRef) = lower(:ref) "
            + "and (b.agencyId is null or b.agencyId = :agencyId) order by b.agencyId nulls last, b.id")
    List<Booking> findByRef(Collection<Long> unitIds, Long agencyId, String ref);

    /** Number of bookings per agency on the given units: rows of {agencyId, count}. */
    @Query("select b.agencyId, count(b) from Booking b where b.unitId in :unitIds and b.agencyId is not null "
            + "group by b.agencyId")
    List<Object[]> countByAgency(Collection<Long> unitIds);

    boolean existsByAgencyId(Long agencyId);

    /** True if a booking of each agency has the same number on the same unit. */
    @Query("select count(a) > 0 from Booking a, Booking b where a.agencyId = :sourceId and b.agencyId = :targetId "
            + "and a.unitId = b.unitId and lower(a.externalRef) = lower(b.externalRef)")
    boolean refsCollide(Long sourceId, Long targetId);

    @Modifying
    @Query("update Booking b set b.agencyId = :targetId where b.agencyId = :sourceId")
    void moveAgency(Long sourceId, Long targetId);

    @Query("select b from Booking b where b.unitId in :unitIds "
            + "and (cast(:from as LocalDate) is null or b.departure > :from) "
            + "and (cast(:to as LocalDate) is null or b.arrival <= :to)")
    List<Booking> findForUnits(Collection<Long> unitIds, LocalDate from, LocalDate to);

    /** The stay with exactly this period on the unit; the overlap constraint allows at most one. */
    List<Booking> findByUnitIdAndArrivalAndDepartureAndCancelledFalse(Long unitId, LocalDate arrival,
            LocalDate departure);

    boolean existsByUnitIdIn(Collection<Long> unitIds);
}
