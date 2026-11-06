package app.logis.core.realestate;

import app.logis.core.ledger.LegalEntityUsage;
import org.springframework.stereotype.Component;

@Component
class PropertyLegalEntityUsage implements LegalEntityUsage {

    private final PropertyRepository properties;

    PropertyLegalEntityUsage(PropertyRepository properties) {
        this.properties = properties;
    }

    @Override
    public String blockingReason(Long legalEntityId) {
        return properties.existsByLegalEntityId(legalEntityId) ? "Der Rechtsträger hat noch Objekte" : null;
    }
}
