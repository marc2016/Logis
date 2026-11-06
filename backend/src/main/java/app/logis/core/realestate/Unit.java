package app.logis.core.realestate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "units", schema = "core")
class Unit {

    /** Object type for sharing; a child of {@link Property#SHARE_TYPE}. */
    static final String SHARE_TYPE = "unit";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private Long propertyId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UnitUsage usage;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Unit() {
    }

    Unit(Long propertyId, String name, UnitUsage usage, int sortOrder, Instant createdAt) {
        this.propertyId = propertyId;
        this.name = name;
        this.usage = usage;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getPropertyId() {
        return propertyId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    UnitUsage getUsage() {
        return usage;
    }

    void setUsage(UnitUsage usage) {
        this.usage = usage;
    }

    boolean isArchived() {
        return archived;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }

    int getSortOrder() {
        return sortOrder;
    }
}
