package app.logis.core.realestate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

interface PropertyCostRepository extends JpaRepository<PropertyCost, Long> {

    boolean existsByPropertyId(Long propertyId);

    @Query("select c from PropertyCost c where c.propertyId = :propertyId and c.date between :from and :to")
    List<PropertyCost> findInPeriod(Long propertyId, LocalDate from, LocalDate to);
}
