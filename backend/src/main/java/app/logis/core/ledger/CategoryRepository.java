package app.logis.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c order by c.kind, c.sortOrder, lower(c.name)")
    List<Category> findAllOrdered();

    List<Category> findByParentId(Long parentId);

    Optional<Category> findByModuleKey(String moduleKey);

    @Query("select count(c) > 0 from Category c where c.kind = :kind and lower(c.name) = lower(:name) "
            + "and ((:parentId is null and c.parentId is null) or c.parentId = :parentId) "
            + "and (:excludeId is null or c.id <> :excludeId)")
    boolean nameTaken(CategoryKind kind, Long parentId, String name, Long excludeId);
}
