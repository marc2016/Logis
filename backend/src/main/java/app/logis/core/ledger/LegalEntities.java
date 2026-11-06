package app.logis.core.ledger;

import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public facade for other core packages (e.g. real estate) that hang data off legal entities.
 */
@Service
@Transactional(readOnly = true)
public class LegalEntities {

    /** What others may see of a legal entity: no tax number, no manager. */
    public record Summary(Long id, String name, LegalEntityKind kind) {
    }

    private final LegalEntityRepository entities;

    LegalEntities(LegalEntityRepository entities) {
        this.entities = entities;
    }

    /** The legal entity if the user manages it; otherwise 404. */
    public Summary requireManaged(Long id, Long userId) {
        return entities.findManaged(id, userId).map(LegalEntities::summary)
                .orElseThrow(() -> ApiException.notFound(LegalEntityService.NOT_FOUND));
    }

    public Map<Long, Summary> summaries(Collection<Long> ids) {
        return entities.findAllById(ids).stream().map(LegalEntities::summary)
                .collect(Collectors.toMap(Summary::id, Function.identity()));
    }

    private static Summary summary(LegalEntity entity) {
        return new Summary(entity.getId(), entity.getName(), entity.getKind());
    }
}
