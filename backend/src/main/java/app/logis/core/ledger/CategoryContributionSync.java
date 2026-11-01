package app.logis.core.ledger;

import app.logis.core.modules.CategoryContribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Creates missing module categories at startup (by their key). Existing ones are never
 * renamed or moved. Does not run for disabled modules either - their beans are missing.
 */
@Component
class CategoryContributionSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryContributionSync.class);

    private final CategoryRepository categories;
    private final List<CategoryContribution> contributions;

    CategoryContributionSync(CategoryRepository categories, List<CategoryContribution> contributions) {
        this.categories = categories;
        this.contributions = contributions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Main categories first, so that subcategories find their parent.
        contributions.stream()
                .sorted(Comparator.comparing(c -> c.parentKey() != null))
                .forEach(this::ensure);
    }

    private void ensure(CategoryContribution c) {
        if (categories.findByModuleKey(c.key()).isPresent()) {
            return;
        }
        Long parentId = null;
        var kind = c.income() ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (c.parentKey() != null) {
            var parent = categories.findByModuleKey(c.parentKey()).orElse(null);
            if (parent == null) {
                log.warn("Module category '{}': main category '{}' is missing - skipped", c.key(), c.parentKey());
                return;
            }
            parentId = parent.getId();
            kind = parent.getKind();
        }
        if (categories.nameTaken(kind, parentId, c.name(), null)) {
            log.warn("Module category '{}': name '{}' is already taken - skipped", c.key(), c.name());
            return;
        }
        categories.save(new Category(parentId, kind, c.name(), c.key(), c.moduleId()));
        log.info("Module category '{}' created", c.key());
    }
}
