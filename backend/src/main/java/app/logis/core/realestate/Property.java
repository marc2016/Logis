package app.logis.core.realestate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "properties", schema = "core")
class Property {

    /** Object type for sharing. */
    static final String SHARE_TYPE = "property";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_entity_id", nullable = false, updatable = false)
    private Long legalEntityId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 128)
    private String street;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(length = 128)
    private String city;

    /** Only drives the interface: single-unit properties hide their unit level. */
    @Column(name = "multi_unit", nullable = false)
    private boolean multiUnit;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Property() {
    }

    Property(Long legalEntityId, String name, Address address, boolean multiUnit, Instant createdAt) {
        this.legalEntityId = legalEntityId;
        this.name = name;
        setAddress(address);
        this.multiUnit = multiUnit;
        this.createdAt = createdAt;
    }

    record Address(String street, String postalCode, String city) {
    }

    Long getId() {
        return id;
    }

    Long getLegalEntityId() {
        return legalEntityId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Address getAddress() {
        return new Address(street, postalCode, city);
    }

    void setAddress(Address address) {
        this.street = address.street();
        this.postalCode = address.postalCode();
        this.city = address.city();
    }

    boolean isMultiUnit() {
        return multiUnit;
    }

    void setMultiUnit(boolean multiUnit) {
        this.multiUnit = multiUnit;
    }

    boolean isArchived() {
        return archived;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }
}
