package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitSummary;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Statements of a rental service provider per unit, with one line per settled booking. */
@Service
@Transactional
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class StatementService {

    static final String NOT_FOUND = "Abrechnung nicht gefunden";

    private final StatementRepository statements;
    private final StatementLineRepository lines;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final RentalAccess access;
    private final Clock clock;

    StatementService(StatementRepository statements, StatementLineRepository lines, BookingRepository bookings,
            BookingService bookingService, RentalAccess access, Clock clock) {
        this.statements = statements;
        this.lines = lines;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.access = access;
        this.clock = clock;
    }

    record StatementInput(Long unitId, String provider, LocalDate periodFrom, LocalDate periodTo,
            LocalDate statementDate) {
    }

    record LineInput(BigDecimal gross, BigDecimal commission, BigDecimal cleaning) {
    }

    record LineDetails(StatementLine line, BookingService.BookingDetails booking) {
    }

    record StatementDetails(Statement statement, UnitSummary unit, ShareRole role, BigDecimal payout, int lineCount,
            List<LineDetails> lines) {

        boolean canEdit() {
            return RentalAccess.canEdit(role);
        }
    }

    // --- Reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<StatementDetails> list(Long userId, Long unitId) {
        var roles = access.visibleUnits(userId);
        var unitIds = roles.keySet().stream().filter(id -> unitId == null || unitId.equals(id)).toList();
        if (unitIds.isEmpty()) {
            return List.of();
        }
        var list = statements.findForUnits(unitIds);
        var summaries = access.summaries(unitIds);
        var linesByStatement = lines.findByStatementIdIn(list.stream().map(Statement::getId).toList()).stream()
                .collect(Collectors.groupingBy(StatementLine::getStatementId));
        return list.stream()
                .map(s -> {
                    var own = linesByStatement.getOrDefault(s.getId(), List.of());
                    return new StatementDetails(s, summaries.get(s.getUnitId()), roles.get(s.getUnitId()),
                            payout(own), own.size(), List.of());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    StatementDetails get(Long userId, Long id) {
        var statement = statements.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var role = access.require(statement.getUnitId(), userId, ShareRole.VIEWER, NOT_FOUND);
        var own = lines.findByStatementId(id);
        var bookingById = bookingService.details(own.stream().map(StatementLine::getBookingId).toList(),
                        Map.of(statement.getUnitId(), role)).stream()
                .collect(Collectors.toMap(d -> d.booking().getId(), Function.identity()));
        var lineDetails = own.stream()
                .map(l -> new LineDetails(l, bookingById.get(l.getBookingId())))
                .sorted(Comparator.comparing((LineDetails l) -> l.booking().booking().getArrival())
                        .thenComparing(l -> l.line().getBookingId()))
                .toList();
        var unit = access.summaries(List.of(statement.getUnitId())).get(statement.getUnitId());
        return new StatementDetails(statement, unit, role, payout(own), own.size(), lineDetails);
    }

    @Transactional(readOnly = true)
    List<String> providers(Long userId, Long unitId) {
        access.require(unitId, userId, ShareRole.VIEWER, BookingService.UNIT_NOT_FOUND);
        return statements.providers(unitId);
    }

    // --- Writing -------------------------------------------------------------------------------

    StatementDetails create(Long userId, StatementInput input) {
        access.require(input.unitId(), userId, ShareRole.EDITOR, BookingService.UNIT_NOT_FOUND);
        var statement = new Statement(input.unitId(), clock.instant());
        apply(statement, input);
        return get(userId, statements.save(statement).getId());
    }

    StatementDetails update(Long userId, Long id, StatementInput input, Long version) {
        var statement = requireEditable(userId, id);
        if (version == null || version != statement.getVersion()) {
            throw ApiException.conflict("Die Abrechnung wurde inzwischen geändert");
        }
        apply(statement, input);
        statements.flush();
        return get(userId, id);
    }

    void delete(Long userId, Long id) {
        var statement = requireEditable(userId, id);
        lines.deleteByStatementId(id);
        statements.delete(statement);
    }

    /** Assigns a booking of the same unit or updates its amounts; gross defaults to the expected rent. */
    StatementDetails putLine(Long userId, Long id, Long bookingId, LineInput input) {
        var statement = requireEditable(userId, id);
        var booking = bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound(BookingService.NOT_FOUND));
        if (access.role(booking.getUnitId(), userId).isEmpty()) {
            throw ApiException.notFound(BookingService.NOT_FOUND);
        }
        if (!booking.getUnitId().equals(statement.getUnitId())) {
            throw ApiException.badRequest("Die Buchung gehört zu einer anderen Einheit");
        }
        var line = lines.findById(bookingId).orElse(null);
        if (line != null && !line.getStatementId().equals(id)) {
            throw ApiException.conflict("Die Buchung ist bereits abgerechnet");
        }
        var gross = input.gross() != null ? input.gross()
                : line != null ? line.getGross() : booking.getExpectedRent();
        if (line == null) {
            line = new StatementLine(bookingId, id);
        }
        line.setAmounts(Money.require(gross, "Bitte einen Bruttobetrag angeben."),
                Money.orZero(input.commission()), Money.orZero(input.cleaning()));
        lines.save(line);
        return get(userId, id);
    }

    StatementDetails removeLine(Long userId, Long id, Long bookingId) {
        requireEditable(userId, id);
        var line = lines.findById(bookingId).filter(l -> l.getStatementId().equals(id))
                .orElseThrow(() -> ApiException.notFound(BookingService.NOT_FOUND));
        lines.delete(line);
        lines.flush();
        return get(userId, id);
    }

    // --- Helpers -------------------------------------------------------------------------------

    private Statement requireEditable(Long userId, Long id) {
        var statement = statements.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        access.require(statement.getUnitId(), userId, ShareRole.EDITOR, NOT_FOUND);
        return statement;
    }

    private static void apply(Statement statement, StatementInput input) {
        var provider = input.provider() == null ? "" : input.provider().trim();
        if (provider.isEmpty()) {
            throw ApiException.badRequest("Bitte einen Dienstleister angeben.");
        }
        if (provider.length() > 128) {
            throw ApiException.badRequest("Der Dienstleister darf höchstens 128 Zeichen lang sein.");
        }
        if (input.periodFrom() == null || input.periodTo() == null || input.statementDate() == null) {
            throw ApiException.badRequest("Bitte Zeitraum und Abrechnungsdatum angeben.");
        }
        if (input.periodTo().isBefore(input.periodFrom())) {
            throw ApiException.badRequest("Das Ende des Zeitraums darf nicht vor dem Anfang liegen.");
        }
        statement.apply(provider, input.periodFrom(), input.periodTo(), input.statementDate());
    }

    static BigDecimal payout(List<StatementLine> own) {
        return own.stream().map(StatementLine::net).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
