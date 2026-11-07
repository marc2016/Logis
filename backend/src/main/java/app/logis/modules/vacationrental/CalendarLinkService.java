package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitUsage;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Subscription links of a unit and the feeds behind them. */
@Service
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class CalendarLinkService {

    static final String NOT_FOUND = "Link nicht gefunden";

    private final CalendarLinkRepository links;
    private final BookingRepository bookings;
    private final RentalAccess access;
    private final Clock clock;

    CalendarLinkService(CalendarLinkRepository links, BookingRepository bookings, RentalAccess access, Clock clock) {
        this.links = links;
        this.bookings = bookings;
        this.access = access;
        this.clock = clock;
    }

    record LinkView(Long id, Long unitId, String name, boolean withGuests, Instant createdAt) {
        static LinkView of(CalendarLink link) {
            return new LinkView(link.getId(), link.getUnitId(), link.getName(), link.isWithGuests(), link.getCreatedAt());
        }
    }

    /** A new link with its feed path; the token is only known at this moment. */
    record CreatedLink(LinkView link, String path) {
    }

    @Transactional(readOnly = true)
    List<LinkView> list(Long userId, Long unitId) {
        requireRentalUnit(unitId, userId);
        return links.findByUnitIdAndCreatedByOrderByCreatedAt(unitId, userId).stream().map(LinkView::of).toList();
    }

    @Transactional
    CreatedLink create(Long userId, Long unitId, String name, boolean withGuests) {
        requireRentalUnit(unitId, userId);
        var trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw ApiException.badRequest("Bitte einen Namen mit höchstens 80 Zeichen angeben.");
        }
        var token = CalendarLink.newToken();
        var link = links.save(new CalendarLink(unitId, userId, trimmed, withGuests, token, Instant.now(clock)));
        return new CreatedLink(LinkView.of(link), "/api/ical/" + token + ".ics");
    }

    @Transactional
    void revoke(Long userId, Long linkId) {
        var link = links.findById(linkId).filter(l -> l.getCreatedBy().equals(userId))
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        links.delete(link);
    }

    /** The feed of a link, or empty when the token is unknown or its creator lost access to the unit. */
    @Transactional(readOnly = true)
    Optional<String> feed(String token) {
        return links.findByTokenHash(CalendarLink.hash(token))
                .filter(link -> access.role(link.getUnitId(), link.getCreatedBy()).isPresent())
                .map(link -> {
                    var today = LocalDate.now(clock);
                    var stays = bookings.findForUnits(Set.of(link.getUnitId()), CalendarFeed.since(today).minusDays(1), null)
                            .stream()
                            .filter(b -> !b.isCancelled())
                            .sorted(Comparator.comparing(Booking::getArrival))
                            .toList();
                    return CalendarFeed.write(link.getName(), stays, link.isWithGuests(), Instant.now(clock));
                });
    }

    private void requireRentalUnit(Long unitId, Long userId) {
        access.require(unitId, userId, ShareRole.VIEWER, BookingService.UNIT_NOT_FOUND);
        var unit = access.summaries(Set.of(unitId)).get(unitId);
        if (unit == null || unit.usage() != UnitUsage.VACATION_RENTAL) {
            throw ApiException.notFound(BookingService.UNIT_NOT_FOUND);
        }
    }
}
