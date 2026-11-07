package app.logis.core.ledger;

import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public facade for other core packages (e.g. real estate costs) that refer to categories.
 */
@Service
@Transactional(readOnly = true)
public class Categories {

    private final CategoryService service;
    private final CategoryRepository categories;

    Categories(CategoryService service, CategoryRepository categories) {
        this.service = service;
        this.categories = categories;
    }

    /** The ID if the category is selectable (active, its module enabled) and an expense category; otherwise 400. */
    public Long requireSelectableExpense(Long id) {
        var category = service.requireSelectable(id);
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw ApiException.badRequest(CategoryService.NOT_AVAILABLE);
        }
        return category.getId();
    }

    /** Display names of the given categories; unknown IDs are left out. */
    public Map<Long, String> names(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categories.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }
}
