package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The property name an agency uses in its mails, mapped to a unit, per mailbox owner. */
@Entity
@Table(name = "unit_mappings", schema = "vacationrental")
class UnitMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    protected UnitMapping() {
    }

    UnitMapping(Long ownerUserId, Long agencyId, String propertyName, Long unitId) {
        this.ownerUserId = ownerUserId;
        this.agencyId = agencyId;
        this.propertyName = propertyName;
        this.unitId = unitId;
    }

    Long getOwnerUserId() {
        return ownerUserId;
    }

    String getPropertyName() {
        return propertyName;
    }

    Long getUnitId() {
        return unitId;
    }

    void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
    }

    void setUnitId(Long unitId) {
        this.unitId = unitId;
    }
}
