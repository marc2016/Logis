package app.logis.core.ledger;

import app.logis.core.modules.ModuleDescriptor;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared category tree with at most two levels. Maintained by admins only
 * (secured via /api/admin/**); module categories can only be archived.
 */
@Service
@Transactional
class CategoryService {

    static final String NOT_AVAILABLE = "Die Kategorie ist nicht verfügbar";
    static final String MODULE_MANAGED = "Diese Kategorie wird von einem Modul verwaltet";

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Set<String> activeModules;

    CategoryService(CategoryRepository categories, TransactionRepository transactions,
            List<ModuleDescriptor> modules) {
        this.categories = categories;
        this.transactions = transactions;
        this.activeModules = modules.stream().map(ModuleDescriptor::id).collect(Collectors.toSet());
    }

    record Node(Category category, List<Node> children) {
    }

    /** Selection tree: active categories only, without categories of disabled modules. */
    @Transactional(readOnly = true)
    List<Node> selectableTree() {
        return tree(categories.findAllOrdered().stream().filter(this::isSelectable).toList());
    }

    /** Management tree: all categories including archived ones. */
    @Transactional(readOnly = true)
    List<Node> fullTree() {
        return tree(categories.findAllOrdered());
    }

    @Transactional(readOnly = true)
    Map<Long, Category> byIds(Set<Long> ids) {
        return categories.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, c -> c));
    }

    /** For transactions: the category must exist and be selectable. */
    @Transactional(readOnly = true)
    Category requireSelectable(Long id) {
        return categories.findById(id).filter(this::isSelectable)
                .orElseThrow(() -> ApiException.badRequest(NOT_AVAILABLE));
    }

    /** The category and its subcategories (for filters). */
    @Transactional(readOnly = true)
    Set<Long> withChildren(Long id) {
        var ids = new java.util.HashSet<Long>();
        ids.add(id);
        categories.findByParentId(id).forEach(c -> ids.add(c.getId()));
        return ids;
    }

    Category create(String name, CategoryKind kind, Long parentId) {
        var clean = requireName(name);
        CategoryKind effectiveKind = kind;
        if (parentId != null) {
            var parent = requireRootParent(parentId);
            effectiveKind = parent.getKind();
        } else if (kind == null) {
            throw ApiException.badRequest("Bitte eine Art wählen.");
        }
        requireUniqueName(effectiveKind, parentId, clean, null);
        return categories.save(new Category(parentId, effectiveKind, clean, null, null));
    }

    Category update(Long id, String name, Long parentId) {
        var category = load(id);
        if (category.isModuleManaged()) {
            throw ApiException.conflict(MODULE_MANAGED);
        }
        var clean = requireName(name);
        if (!Objects.equals(parentId, category.getParentId())) {
            if (parentId != null) {
                var parent = requireRootParent(parentId);
                if (parent.getId().equals(id)) {
                    throw ApiException.badRequest("Eine Kategorie kann nicht unter sich selbst liegen.");
                }
                if (!categories.findByParentId(id).isEmpty()) {
                    throw ApiException.badRequest("Kategorien haben höchstens zwei Ebenen.");
                }
                if (parent.getKind() != category.getKind()) {
                    throw ApiException.badRequest("Die Kategorie kann nur innerhalb ihrer Art verschoben werden.");
                }
            }
            category.setParentId(parentId);
        }
        requireUniqueName(category.getKind(), parentId, clean, id);
        category.setName(clean);
        return category;
    }

    Category setArchived(Long id, boolean archived) {
        var category = load(id);
        category.setArchived(archived);
        return category;
    }

    void delete(Long id) {
        var category = load(id);
        if (category.isModuleManaged()) {
            throw ApiException.conflict(MODULE_MANAGED);
        }
        if (!categories.findByParentId(id).isEmpty()) {
            throw ApiException.conflict("Die Kategorie hat noch Unterkategorien");
        }
        if (transactions.countByCategoryId(id) > 0) {
            throw ApiException.conflict("Die Kategorie wird verwendet und kann nur archiviert werden");
        }
        categories.delete(category);
    }

    private boolean isSelectable(Category c) {
        return !c.isArchived() && (c.getModuleId() == null || activeModules.contains(c.getModuleId()));
    }

    private Category requireRootParent(Long parentId) {
        var parent = categories.findById(parentId)
                .orElseThrow(() -> ApiException.badRequest("Die Hauptkategorie existiert nicht."));
        if (parent.getParentId() != null) {
            throw ApiException.badRequest("Kategorien haben höchstens zwei Ebenen.");
        }
        return parent;
    }

    private void requireUniqueName(CategoryKind kind, Long parentId, String name, Long excludeId) {
        if (categories.nameTaken(kind, parentId, name, excludeId)) {
            throw ApiException.conflict("Eine Kategorie mit diesem Namen existiert bereits");
        }
    }

    private Category load(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Kategorie nicht gefunden"));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Bitte einen Namen angeben.");
        }
        if (name.trim().length() > 64) {
            throw ApiException.badRequest("Der Name darf höchstens 64 Zeichen lang sein.");
        }
        return name.trim();
    }

    private static List<Node> tree(List<Category> list) {
        var children = list.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        var roots = new ArrayList<Node>();
        for (var c : list) {
            if (c.getParentId() == null) {
                roots.add(new Node(c, children.getOrDefault(c.getId(), List.of()).stream()
                        .map(child -> new Node(child, List.of())).toList()));
            }
        }
        return roots;
    }
}
