package app.logis.core.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories", schema = "core")
class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CategoryKind kind;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "module_key", length = 128, updatable = false)
    private String moduleKey;

    @Column(name = "module_id", length = 64, updatable = false)
    private String moduleId;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
    }

    Category(Long parentId, CategoryKind kind, String name, String moduleKey, String moduleId) {
        this.parentId = parentId;
        this.kind = kind;
        this.name = name;
        this.moduleKey = moduleKey;
        this.moduleId = moduleId;
    }

    boolean isModuleManaged() {
        return moduleKey != null;
    }

    Long getId() {
        return id;
    }

    Long getParentId() {
        return parentId;
    }

    void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    CategoryKind getKind() {
        return kind;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getModuleKey() {
        return moduleKey;
    }

    String getModuleId() {
        return moduleId;
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
