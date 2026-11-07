package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StatementLineRepository extends JpaRepository<StatementLine, Long> {

    List<StatementLine> findByBookingIdIn(Collection<Long> bookingIds);

    List<StatementLine> findByStatementId(Long statementId);

    List<StatementLine> findByStatementIdIn(Collection<Long> statementIds);

    void deleteByStatementId(Long statementId);
}
