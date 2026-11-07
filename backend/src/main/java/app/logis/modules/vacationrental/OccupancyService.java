package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitUsage;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Calendar and yearly overview, derived from bookings and statements. */
@Service
@Transactional(readOnly = true)
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class OccupancyService {

    private final BookingRepository bookings;
    private final StatementRepository statements;
    private final StatementLineRepository lines;
    private final BookingService bookingService;
    private final RentalAccess access;

    OccupancyService(BookingRepository bookings, StatementRepository statements, StatementLineRepository lines,
            BookingService bookingService, RentalAccess access) {
        this.bookings = bookings;
        this.statements = statements;
        this.lines = lines;
        this.bookingService = bookingService;
        this.access = access;
    }

    record CalendarBooking(Long id, LocalDate arrival, LocalDate departure, BookingStatus status, String guestName,
            String externalRef) {
    }

    record CalendarUnit(Long id, String name, boolean archived, boolean canEdit, List<CalendarBooking> bookings) {
    }

    record Calendar(Long propertyId, String propertyName, YearMonth month, List<CalendarUnit> units) {
    }

    record MonthFigures(int month, int nights, int occupancy, BigDecimal settledNet, BigDecimal plannedRent) {
    }

    record Overview(Long unitId, int year, List<MonthFigures> months, MonthFigures total) {
    }

    /** Rows: the property's vacation rental units visible to the user; bars: stays that are not cancelled. */
    Calendar calendar(Long userId, Long propertyId, YearMonth month) {
        var roles = access.visibleUnits(userId);
        var units = access.summaries(roles.keySet()).values().stream()
                .filter(u -> u.propertyId().equals(propertyId) && u.usage() == UnitUsage.VACATION_RENTAL)
                .sorted(Comparator.comparing(u -> u.name().toLowerCase()))
                .toList();
        if (units.isEmpty()) {
            throw ApiException.notFound("Objekt nicht gefunden");
        }
        var unitIds = units.stream().map(u -> u.id()).toList();
        var found = bookings.findForUnits(unitIds, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(b -> !b.isCancelled())
                .map(b -> b.getId())
                .toList();
        var byUnit = bookingService.details(found, roles).stream()
                .collect(Collectors.groupingBy(d -> d.booking().getUnitId()));
        var rows = units.stream()
                .map(u -> new CalendarUnit(u.id(), u.name(), u.archived() || u.propertyArchived(),
                        RentalAccess.canEdit(roles.get(u.id())),
                        byUnit.getOrDefault(u.id(), List.of()).stream()
                                .sorted(Comparator.comparing(d -> d.booking().getArrival()))
                                .map(d -> new CalendarBooking(d.booking().getId(), d.booking().getArrival(),
                                        d.booking().getDeparture(), d.status(), d.guest().name(),
                                        d.booking().getExternalRef()))
                                .toList()))
                .toList();
        return new Calendar(propertyId, units.getFirst().propertyName(), month, rows);
    }

    Overview overview(Long userId, Long unitId, int year) {
        access.require(unitId, userId, ShareRole.VIEWER, BookingService.UNIT_NOT_FOUND);
        var nights = new int[12];
        var settled = zeros();
        var planned = zeros();

        var stays = bookings.findForUnits(List.of(unitId), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        var settledIds = lines.findByBookingIdIn(stays.stream().map(Booking::getId).toList()).stream()
                .map(StatementLine::getBookingId).collect(Collectors.toSet());
        for (var b : stays) {
            if (b.isCancelled()) {
                continue;
            }
            var perMonth = Occupancy.nightsPerMonth(b.getArrival(), b.getDeparture(), year);
            for (int m = 0; m < 12; m++) {
                nights[m] += perMonth[m];
            }
            if (!settledIds.contains(b.getId()) && b.getExpectedRent() != null && b.getArrival().getYear() == year) {
                var m = b.getArrival().getMonthValue() - 1;
                planned.set(m, planned.get(m).add(b.getExpectedRent()));
            }
        }
        var inYear = statements.findForUnitsInPeriod(List.of(unitId), LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        var dateByStatement = inYear.stream().collect(Collectors.toMap(Statement::getId, Statement::getStatementDate));
        for (var line : lines.findByStatementIdIn(dateByStatement.keySet())) {
            var m = dateByStatement.get(line.getStatementId()).getMonthValue() - 1;
            settled.set(m, settled.get(m).add(line.net()));
        }

        var months = new ArrayList<MonthFigures>();
        for (int m = 0; m < 12; m++) {
            var days = YearMonth.of(year, m + 1).lengthOfMonth();
            months.add(new MonthFigures(m + 1, nights[m], Occupancy.rate(nights[m], days), settled.get(m),
                    planned.get(m)));
        }
        var totalNights = months.stream().mapToInt(MonthFigures::nights).sum();
        var total = new MonthFigures(0, totalNights, Occupancy.rate(totalNights, Occupancy.daysInYear(year)),
                settled.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add),
                planned.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        return new Overview(unitId, year, months, total);
    }

    private static List<BigDecimal> zeros() {
        var list = new ArrayList<BigDecimal>();
        for (int m = 0; m < 12; m++) {
            list.add(BigDecimal.ZERO.setScale(2));
        }
        return list;
    }
}
