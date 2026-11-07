package app.logis.core.realestate;

import app.logis.core.documents.DocumentOwnerType;
import org.springframework.stereotype.Component;

/** Receipts on costs: visible with any role on the cost's target, changeable with EDITOR. */
@Component
class PropertyCostDocumentOwner implements DocumentOwnerType {

    private final PropertyCostService costs;

    PropertyCostDocumentOwner(PropertyCostService costs) {
        this.costs = costs;
    }

    @Override
    public String type() {
        return PropertyCost.DOCUMENT_TYPE;
    }

    @Override
    public boolean canRead(Long ownerId, Long userId) {
        return costs.canRead(ownerId, userId);
    }

    @Override
    public boolean canWrite(Long ownerId, Long userId) {
        return costs.canWrite(ownerId, userId);
    }
}
