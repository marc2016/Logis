package app.logis.modules.vacationrental;

import app.logis.core.documents.DocumentOwnerType;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.stereotype.Component;

/**
 * Statement documents name tenants, like booking documents: reading and writing both need EDITOR on the unit (spec
 * rental-statements, „Documents of a statement“).
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class StatementDocumentOwner implements DocumentOwnerType {

    private final StatementService statements;

    StatementDocumentOwner(StatementService statements) {
        this.statements = statements;
    }

    @Override
    public String type() {
        return Statement.SOURCE_TYPE;
    }

    @Override
    public boolean canRead(Long ownerId, Long userId) {
        return statements.canEdit(ownerId, userId);
    }

    @Override
    public boolean canWrite(Long ownerId, Long userId) {
        return statements.canEdit(ownerId, userId);
    }
}
