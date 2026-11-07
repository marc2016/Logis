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

    @Column(nullable = false)
    private String agency;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    protected UnitMapping() {
    }

    UnitMapping(Long ownerUserId, String agency, String propertyName, Long unitId) {
        this.ownerUserId = ownerUserId;
        this.agency = agency;
        this.propertyName = propertyName;
        this.unitId = unitId;
    }

    Long getUnitId() {
        return unitId;
    }

    void setUnitId(Long unitId) {
        this.unitId = unitId;
    }
}
