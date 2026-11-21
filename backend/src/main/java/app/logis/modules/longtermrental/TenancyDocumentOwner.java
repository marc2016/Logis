package app.logis.modules.longtermrental;

import app.logis.core.documents.DocumentOwnerType;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.stereotype.Component;

/** Tenancy documents such as the contract contain tenant data: reading and writing both need EDITOR on the unit. */
@Component
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class TenancyDocumentOwner implements DocumentOwnerType {

    static final String TYPE = "longtermrental.tenancy";

    private final TenancyService tenancies;

    TenancyDocumentOwner(TenancyService tenancies) {
        this.tenancies = tenancies;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean canRead(Long ownerId, Long userId) {
        return tenancies.canEdit(ownerId, userId);
    }

    @Override
    public boolean canWrite(Long ownerId, Long userId) {
        return tenancies.canEdit(ownerId, userId);
    }
}
