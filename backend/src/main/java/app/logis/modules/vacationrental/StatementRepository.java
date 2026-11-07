package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

interface StatementRepository extends JpaRepository<Statement, Long> {

    @Query("select s from Statement s where s.unitId in :unitIds order by s.statementDate desc, s.id desc")
    List<Statement> findForUnits(Collection<Long> unitIds);

    @Query("select s from Statement s where s.unitId in :unitIds and s.statementDate between :from and :to")
    List<Statement> findForUnitsInPeriod(Collection<Long> unitIds, LocalDate from, LocalDate to);

    @Query("select s.provider from Statement s where s.unitId = :unitId group by s.provider "
            + "order by max(s.statementDate) desc")
    List<String> providers(Long unitId);

    boolean existsByUnitIdIn(Collection<Long> unitIds);
}
