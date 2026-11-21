package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StatementOpenLineRepository extends JpaRepository<StatementOpenLine, Long> {

    List<StatementOpenLine> findByStatementIdOrderByArrivalAscIdAsc(Long statementId);

    List<StatementOpenLine> findByStatementIdIn(Collection<Long> statementIds);

    void deleteByStatementId(Long statementId);
}
