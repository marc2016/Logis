package app.logis.core.ledger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Auswahl fuer alle Benutzer unter /api/categories, Pflege nur fuer Admins unter /api/admin/categories.
 */
@RestController
class CategoryController {

    private final CategoryService service;

    CategoryController(CategoryService service) {
        this.service = service;
    }

    record CategoryView(Long id, String name, CategoryKind kind, Long parentId, String moduleId, boolean archived,
            List<CategoryView> children) {

        static CategoryView of(CategoryService.Node node) {
            var c = node.category();
            return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getParentId(), c.getModuleId(),
                    c.isArchived(), node.children().stream().map(CategoryView::of).toList());
        }
    }

    record CreateCategoryRequest(String name, CategoryKind kind, Long parentId) {
    }

    record UpdateCategoryRequest(String name, Long parentId) {
    }

    @GetMapping("/api/categories")
    List<CategoryView> selectable() {
        return service.selectableTree().stream().map(CategoryView::of).toList();
    }

    @GetMapping("/api/admin/categories")
    List<CategoryView> all() {
        return service.fullTree().stream().map(CategoryView::of).toList();
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView create(@RequestBody CreateCategoryRequest body) {
        return single(service.create(body.name(), body.kind(), body.parentId()));
    }

    @PutMapping("/api/admin/categories/{id}")
    CategoryView update(@PathVariable Long id, @RequestBody UpdateCategoryRequest body) {
        return single(service.update(id, body.name(), body.parentId()));
    }

    @PostMapping("/api/admin/categories/{id}/archive")
    CategoryView archive(@PathVariable Long id) {
        return single(service.setArchived(id, true));
    }

    @PostMapping("/api/admin/categories/{id}/restore")
    CategoryView restore(@PathVariable Long id) {
        return single(service.setArchived(id, false));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static CategoryView single(Category c) {
        return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getParentId(), c.getModuleId(), c.isArchived(),
                List.of());
    }
}
