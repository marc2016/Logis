package app.logis.core.ledger;

import app.logis.core.users.UserCreatedEvent;
import app.logis.core.web.ApiException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Legal entities: whoever creates one manages it. Every user has exactly one private one.
 */
@Service
@Transactional
class LegalEntityService {

    static final String NOT_FOUND = "Rechtsträger nicht gefunden";

    private final LegalEntityRepository entities;
    private final AccountRepository accounts;
    private final Clock clock;

    LegalEntityService(LegalEntityRepository entities, AccountRepository accounts, Clock clock) {
        this.entities = entities;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Creates the private legal entity when a user is created (same transaction). */
    @EventListener
    void onUserCreated(UserCreatedEvent event) {
        entities.save(new LegalEntity(event.displayName() + " privat", LegalEntityKind.PRIVATE_PERSON, null,
                event.userId(), true, clock.instant()));
    }

    @Transactional(readOnly = true)
    List<LegalEntity> listManaged(Long userId) {
        return entities.findManagedBy(userId);
    }

    @Transactional(readOnly = true)
    LegalEntity requireManaged(Long id, Long userId) {
        return entities.findManaged(id, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    @Transactional(readOnly = true)
    long accountCount(Long entityId) {
        return accounts.countByLegalEntityId(entityId);
    }

    LegalEntity create(Long userId, String name, LegalEntityKind kind, String taxNumber) {
        var clean = requireName(name);
        requireUniqueName(userId, clean, null);
        return entities.save(new LegalEntity(clean, kind, blankToNull(taxNumber), userId, false, clock.instant()));
    }

    LegalEntity update(Long userId, Long id, String name, LegalEntityKind kind, String taxNumber) {
        var entity = requireManaged(id, userId);
        var clean = requireName(name);
        requireUniqueName(userId, clean, id);
        if (entity.isPersonal() && kind != LegalEntityKind.PRIVATE_PERSON) {
            throw ApiException.badRequest("Die Art des privaten Rechtsträgers kann nicht geändert werden.");
        }
        entity.setName(clean);
        entity.setKind(kind);
        entity.setTaxNumber(blankToNull(taxNumber));
        return entity;
    }

    void delete(Long userId, Long id) {
        var entity = requireManaged(id, userId);
        if (entity.isPersonal()) {
            throw ApiException.badRequest("Der private Rechtsträger kann nicht gelöscht werden.");
        }
        if (accounts.countByLegalEntityId(id) > 0) {
            throw ApiException.conflict("Der Rechtsträger hat noch Konten");
        }
        entities.delete(entity);
    }

    private void requireUniqueName(Long userId, String name, Long excludeId) {
        if (entities.nameTaken(userId, name, excludeId)) {
            throw ApiException.conflict("Ein Rechtsträger mit diesem Namen existiert bereits");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Bitte einen Namen angeben.");
        }
        var clean = name.trim();
        if (clean.length() > 128) {
            throw ApiException.badRequest("Der Name darf höchstens 128 Zeichen lang sein.");
        }
        return clean;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
