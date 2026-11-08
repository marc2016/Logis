package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The shared list of agencies (design.md D3): every signed-in user may read and change it. Agencies are reference
 * data, so the sharing model does not apply; only booking counts are limited to the bookings a user can see.
 */
@Service
@Transactional
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class AgencyService {

    static final String NOT_FOUND = "Vermittler nicht gefunden";
    static final String NAME_TAKEN = "Ein Vermittler mit diesem Namen existiert bereits";
    static final String ALIAS_TAKEN = "Dieser Name gehört bereits zu einem anderen Vermittler";
    static final String IN_USE = "Der Vermittler wird noch verwendet und kann nur archiviert werden";
    static final String REFS_COLLIDE = "Eine Buchungsnummer kommt bei beiden Vermittlern auf derselben Einheit vor";
    static final String STALE = "Der Vermittler wurde inzwischen geändert";

    private final AgencyRepository agencies;
    private final AgencyAliasRepository aliases;
    private final BookingRepository bookings;
    private final UnitMappingRepository mappings;
    private final RentalAccess access;
    private final Clock clock;

    AgencyService(AgencyRepository agencies, AgencyAliasRepository aliases, BookingRepository bookings,
            UnitMappingRepository mappings, RentalAccess access, Clock clock) {
        this.agencies = agencies;
        this.aliases = aliases;
        this.bookings = bookings;
        this.mappings = mappings;
        this.access = access;
        this.clock = clock;
    }

    record AgencyDetails(Agency agency, List<AgencyAlias> aliases, long bookingCount) {
    }

    // --- Reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<AgencyDetails> list(Long userId) {
        var all = agencies.findAll();
        var aliasesByAgency = aliases.findByAgencyIdIn(all.stream().map(Agency::getId).toList()).stream()
                .collect(Collectors.groupingBy(AgencyAlias::getAgencyId));
        var units = access.visibleUnits(userId).keySet();
        var counts = new HashMap<Long, Long>();
        if (!units.isEmpty()) {
            for (var row : bookings.countByAgency(units)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return all.stream()
                .sorted(Comparator.comparing((Agency a) -> a.getName().toLowerCase()))
                .map(a -> new AgencyDetails(a, aliasesByAgency.getOrDefault(a.getId(), List.of()).stream()
                        .sorted(Comparator.comparing((AgencyAlias al) -> al.getAlias().toLowerCase()))
                        .toList(), counts.getOrDefault(a.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    AgencyDetails get(Long userId, Long id) {
        return list(userId).stream().filter(d -> d.agency().getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    /** Agencies by id, for showing them on bookings. */
    @Transactional(readOnly = true)
    Map<Long, Agency> byIds(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of()
                : agencies.findAllById(ids).stream().collect(Collectors.toMap(Agency::getId, a -> a));
    }

    /** The agency to put on a booking; an unknown id is rejected. */
    @Transactional(readOnly = true)
    Agency require(Long id) {
        return agencies.findById(id).orElseThrow(() -> ApiException.badRequest(NOT_FOUND));
    }

    // --- Writing -------------------------------------------------------------------------------

    Agency create(String rawName) {
        var name = name(rawName);
        if (agencies.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        if (aliases.findByAliasIgnoreCase(name).isPresent()) {
            throw ApiException.conflict(ALIAS_TAKEN);
        }
        var agency = agencies.saveAndFlush(new Agency(name, clock.instant()));
        aliases.saveAndFlush(new AgencyAlias(agency.getId(), name));
        return agency;
    }

    /** Renaming keeps the previous name as a mail name, so later mails still find the agency. */
    Agency rename(Long id, String rawName, Long version) {
        var agency = requireCurrent(id, version);
        var name = name(rawName);
        if (name.equals(agency.getName())) {
            return agency;
        }
        var sameName = agencies.findByNameIgnoreCase(name);
        if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        var alias = aliases.findByAliasIgnoreCase(name);
        if (alias.isPresent() && !alias.get().getAgencyId().equals(id)) {
            throw ApiException.conflict(ALIAS_TAKEN);
        }
        if (alias.isEmpty()) {
            aliases.save(new AgencyAlias(id, name));
        }
        agency.setName(name);
        return agencies.saveAndFlush(agency);
    }

    Agency setArchived(Long id, boolean archived, Long version) {
        var agency = requireCurrent(id, version);
        agency.setArchived(archived);
        return agencies.saveAndFlush(agency);
    }

    void addAlias(Long id, String rawAlias) {
        var agency = agencies.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var alias = text(rawAlias, AgencyAlias.MAX_LENGTH, "Der Name");
        if (alias == null) {
            throw ApiException.badRequest("Bitte einen Namen angeben.");
        }
        var existing = aliases.findByAliasIgnoreCase(alias);
        if (existing.isPresent()) {
            if (existing.get().getAgencyId().equals(agency.getId())) {
                return;
            }
            throw ApiException.conflict(ALIAS_TAKEN);
        }
        aliases.saveAndFlush(new AgencyAlias(agency.getId(), alias));
    }

    void removeAlias(Long id, Long aliasId) {
        var agency = agencies.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var alias = aliases.findById(aliasId).filter(a -> a.getAgencyId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Name nicht gefunden"));
        if (alias.getAlias().equalsIgnoreCase(agency.getName())) {
            throw ApiException.conflict("Der eigene Name eines Vermittlers kann nicht entfernt werden");
        }
        aliases.delete(alias);
    }

    /** Moves bookings, unit mappings and mail names to the target and deletes the source (design.md D5). */
    Agency merge(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw ApiException.badRequest("Ein Vermittler kann nicht mit sich selbst zusammengeführt werden");
        }
        var source = agencies.findById(sourceId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var target = agencies.findById(targetId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (bookings.refsCollide(sourceId, targetId)) {
            throw ApiException.conflict(REFS_COLLIDE);
        }
        var targetMappings = mappings.findByAgencyId(targetId).stream()
                .map(m -> m.getOwnerUserId() + "|" + m.getPropertyName().toLowerCase())
                .collect(Collectors.toSet());
        for (var mapping : mappings.findByAgencyId(sourceId)) {
            if (targetMappings.contains(mapping.getOwnerUserId() + "|" + mapping.getPropertyName().toLowerCase())) {
                // The target's mapping wins; it cannot conflict with the source's bookings.
                mappings.delete(mapping);
            } else {
                mapping.setAgencyId(targetId);
            }
        }
        mappings.flush();
        bookings.moveAgency(sourceId, targetId);
        aliases.moveAll(sourceId, targetId);
        agencies.delete(source);
        agencies.flush();
        return target;
    }

    void delete(Long id) {
        var agency = agencies.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (bookings.existsByAgencyId(id) || mappings.existsByAgencyId(id)) {
            throw ApiException.conflict(IN_USE);
        }
        agencies.delete(agency);
    }

    // --- Mail import ---------------------------------------------------------------------------

    /**
     * The agency with this mail name; an unknown name creates the agency (design.md D4). Runs in its own
     * transaction, so a concurrent import that created the same agency is found on the second look.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Agency> resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        var alias = rawName.strip();
        if (alias.length() > AgencyAlias.MAX_LENGTH) {
            alias = alias.substring(0, AgencyAlias.MAX_LENGTH);
        }
        var known = agencies.findByAlias(alias);
        if (known.isPresent()) {
            return known;
        }
        var name = alias.length() > Agency.NAME_MAX_LENGTH ? alias.substring(0, Agency.NAME_MAX_LENGTH).strip()
                : alias;
        try {
            var agency = agencies.findByNameIgnoreCase(name)
                    .orElseGet(() -> agencies.saveAndFlush(new Agency(name, clock.instant())));
            if (aliases.findByAliasIgnoreCase(name).isEmpty()) {
                aliases.saveAndFlush(new AgencyAlias(agency.getId(), name));
            }
            if (!alias.equalsIgnoreCase(name)) {
                aliases.saveAndFlush(new AgencyAlias(agency.getId(), alias));
            }
            return Optional.of(agency);
        } catch (DataIntegrityViolationException e) {
            return agencies.findByAlias(alias);
        }
    }

    // --- Helpers -------------------------------------------------------------------------------

    private Agency requireCurrent(Long id, Long version) {
        var agency = agencies.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (version == null || version != agency.getVersion()) {
            throw ApiException.conflict(STALE);
        }
        return agency;
    }

    private static String name(String raw) {
        var name = text(raw, Agency.NAME_MAX_LENGTH, "Der Name");
        if (name == null) {
            throw ApiException.badRequest("Bitte einen Namen angeben.");
        }
        return name;
    }

    private static String text(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.strip();
        if (clean.length() > maxLength) {
            throw ApiException.badRequest(field + " darf höchstens " + maxLength + " Zeichen lang sein.");
        }
        return clean;
    }
}
