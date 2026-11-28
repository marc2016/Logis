package app.logis.modules.longtermrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Rows of {@code longtermrental.rent_payments} (design D1 of rent-payments). */
@Repository
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class RentPaymentStore {

    record Payment(Long id, Long tenancyId, LocalDate date, BigDecimal amount, Long transactionId, String note,
            long version) {
    }

    private static final String COLUMNS = "id, tenancy_id, payment_date, amount, transaction_id, note, version";

    private final JdbcTemplate jdbc;

    RentPaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Payments per tenancy, in order of date and id. */
    Map<Long, List<Payment>> ofTenancies(Collection<Long> tenancyIds) {
        var result = new LinkedHashMap<Long, List<Payment>>();
        if (tenancyIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT " + COLUMNS + " FROM longtermrental.rent_payments WHERE tenancy_id = ANY(?) "
                        + "ORDER BY payment_date, id",
                rs -> {
                    var p = payment(rs, 0);
                    result.computeIfAbsent(p.tenancyId(), k -> new ArrayList<>()).add(p);
                }, (Object) tenancyIds.toArray(Long[]::new));
        return result;
    }

    /** A payment with the unit of its tenancy, for the property finances. */
    record UnitPayment(Long tenancyId, Long unitId, LocalDate date, BigDecimal amount) {
    }

    /** Payments of the given units' tenancies dated between {@code from} and {@code to}. */
    List<UnitPayment> ofUnits(Collection<Long> unitIds, LocalDate from, LocalDate to) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT p.tenancy_id, t.unit_id, p.payment_date, p.amount "
                        + "FROM longtermrental.rent_payments p JOIN longtermrental.tenancies t ON t.id = p.tenancy_id "
                        + "WHERE t.unit_id = ANY(?) AND p.payment_date BETWEEN ? AND ? ORDER BY p.payment_date, p.id",
                (rs, i) -> new UnitPayment(rs.getLong(1), rs.getLong(2), rs.getDate(3).toLocalDate(),
                        rs.getBigDecimal(4)),
                unitIds.toArray(Long[]::new), Date.valueOf(from), Date.valueOf(to));
    }

    Optional<Payment> byId(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM longtermrental.rent_payments WHERE id = ?",
                RentPaymentStore::payment, id).stream().findFirst();
    }

    boolean exists(Long tenancyId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM longtermrental.rent_payments WHERE tenancy_id = ?)", Boolean.class,
                tenancyId));
    }

    /** Inserts the payment; {@link #linkTransaction} sets its transaction in the same database transaction. */
    Long insert(Long tenancyId, LocalDate date, BigDecimal amount, String note) {
        return jdbc.queryForObject("INSERT INTO longtermrental.rent_payments (tenancy_id, payment_date, amount, note) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, tenancyId, Date.valueOf(date), amount, note);
    }

    void linkTransaction(Long id, Long transactionId) {
        jdbc.update("UPDATE longtermrental.rent_payments SET transaction_id = ? WHERE id = ?", transactionId, id);
    }

    /** Returns false if the version no longer matches. */
    boolean update(Long id, long version, LocalDate date, BigDecimal amount, String note) {
        return jdbc.update("UPDATE longtermrental.rent_payments SET payment_date = ?, amount = ?, note = ?, "
                        + "version = version + 1 WHERE id = ? AND version = ?",
                Date.valueOf(date), amount, note, id, version) == 1;
    }

    void delete(Long id) {
        jdbc.update("DELETE FROM longtermrental.rent_payments WHERE id = ?", id);
    }

    private static Payment payment(ResultSet rs, int row) throws SQLException {
        return new Payment(rs.getLong("id"), rs.getLong("tenancy_id"), rs.getDate("payment_date").toLocalDate(),
                rs.getBigDecimal("amount"), rs.getLong("transaction_id"), rs.getString("note"), rs.getLong("version"));
    }
}
