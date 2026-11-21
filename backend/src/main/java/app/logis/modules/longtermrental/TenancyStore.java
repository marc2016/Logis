package app.logis.modules.longtermrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Rows of the schema {@code longtermrental} (design.md of long-term-rental, D2). */
@Repository
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class TenancyStore {

    record Tenancy(Long id, Long unitId, String kind, LocalDate startDate, LocalDate endDate, BigDecimal depositAmount,
            LocalDate depositReceivedOn, boolean tenantsDeleted, long version) {
    }

    record Tenant(Long id, String name, String street, String postalCode, String city, String country, String phone,
            String email) {
    }

    enum ItemKind {
        COLD_RENT, UTILITIES_ADVANCE, OTHER
    }

    record RentItem(ItemKind kind, String label, BigDecimal amount) {
    }

    record RentTerm(Long id, LocalDate validFrom, List<RentItem> items) {

        BigDecimal total() {
            return items.stream().map(RentItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final String COLUMNS = "id, unit_id, kind, start_date, end_date, deposit_amount, "
            + "deposit_received_on, tenants_deleted, version";

    private final JdbcTemplate jdbc;

    TenancyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Tenancy> byId(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM longtermrental.tenancies WHERE id = ?", TenancyStore::tenancy, id)
                .stream().findFirst();
    }

    List<Tenancy> ofUnits(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM longtermrental.tenancies WHERE unit_id = ANY(?) "
                + "ORDER BY start_date, id", TenancyStore::tenancy, (Object) unitIds.toArray(Long[]::new));
    }

    /** Whether another tenancy of the unit overlaps the period; {@code end} null means open. */
    boolean overlaps(Long unitId, LocalDate start, LocalDate end, Long exceptId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM longtermrental.tenancies "
                        + "WHERE unit_id = ? AND id <> ? AND daterange(start_date, end_date, '[]') && daterange(?, ?, '[]'))",
                Boolean.class, unitId, exceptId == null ? -1L : exceptId, Date.valueOf(start),
                end == null ? null : Date.valueOf(end)));
    }

    Long insert(Long unitId, LocalDate start, LocalDate end, BigDecimal depositAmount, LocalDate depositReceivedOn) {
        return jdbc.queryForObject("INSERT INTO longtermrental.tenancies (unit_id, start_date, end_date, deposit_amount, "
                        + "deposit_received_on) VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, unitId,
                Date.valueOf(start), date(end), depositAmount, date(depositReceivedOn));
    }

    /** Returns false if the version no longer matches. */
    boolean update(Long id, long version, Long unitId, LocalDate start, LocalDate end, BigDecimal depositAmount,
            LocalDate depositReceivedOn) {
        return jdbc.update("UPDATE longtermrental.tenancies SET unit_id = ?, start_date = ?, end_date = ?, "
                        + "deposit_amount = ?, deposit_received_on = ?, version = version + 1 WHERE id = ? AND version = ?",
                unitId, Date.valueOf(start), date(end), depositAmount, date(depositReceivedOn), id, version) == 1;
    }

    void touch(Long id) {
        jdbc.update("UPDATE longtermrental.tenancies SET version = version + 1 WHERE id = ?", id);
    }

    void delete(Long id) {
        jdbc.update("DELETE FROM longtermrental.tenancies WHERE id = ?", id);
    }

    // --- Tenants ----------------------------------------------------------------------------------

    Map<Long, List<Tenant>> tenants(Collection<Long> tenancyIds) {
        var result = new LinkedHashMap<Long, List<Tenant>>();
        if (tenancyIds.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT tenancy_id, id, name, street, postal_code, city, country, phone, email "
                + "FROM longtermrental.tenants WHERE tenancy_id = ANY(?) ORDER BY position, id", rs -> {
            result.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Tenant(rs.getLong(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                    rs.getString(9)));
        }, (Object) tenancyIds.toArray(Long[]::new));
        return result;
    }

    void replaceTenants(Long tenancyId, List<Tenant> tenants) {
        jdbc.update("DELETE FROM longtermrental.tenants WHERE tenancy_id = ?", tenancyId);
        for (int i = 0; i < tenants.size(); i++) {
            var t = tenants.get(i);
            jdbc.update("INSERT INTO longtermrental.tenants (tenancy_id, position, name, street, postal_code, city, "
                            + "country, phone, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", tenancyId, i, t.name(),
                    t.street(), t.postalCode(), t.city(), t.country(), t.phone(), t.email());
        }
    }

    /** Removes names and contact data for good; the rows stay, so the number of tenants is still known. */
    void deleteTenantData(Long tenancyId) {
        jdbc.update("UPDATE longtermrental.tenants SET name = NULL, street = NULL, postal_code = NULL, city = NULL, "
                + "country = NULL, phone = NULL, email = NULL WHERE tenancy_id = ?", tenancyId);
        jdbc.update("UPDATE longtermrental.tenancies SET tenants_deleted = TRUE, version = version + 1 WHERE id = ?",
                tenancyId);
    }

    // --- Rent terms -------------------------------------------------------------------------------

    /** Terms per tenancy, oldest first. */
    Map<Long, List<RentTerm>> rentTerms(Collection<Long> tenancyIds) {
        var terms = new LinkedHashMap<Long, List<RentTerm>>();
        if (tenancyIds.isEmpty()) {
            return terms;
        }
        var items = new LinkedHashMap<Long, List<RentItem>>();
        jdbc.query("SELECT i.rent_term_id, i.kind, i.label, i.net_amount FROM longtermrental.rent_items i "
                        + "JOIN longtermrental.rent_terms t ON t.id = i.rent_term_id WHERE t.tenancy_id = ANY(?) "
                        + "ORDER BY i.position, i.id",
                rs -> {
                    items.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                            .add(new RentItem(ItemKind.valueOf(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4)));
                }, (Object) tenancyIds.toArray(Long[]::new));
        jdbc.query("SELECT tenancy_id, id, valid_from FROM longtermrental.rent_terms WHERE tenancy_id = ANY(?) "
                + "ORDER BY valid_from", rs -> {
            terms.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new RentTerm(rs.getLong(2),
                    rs.getDate(3).toLocalDate(), items.getOrDefault(rs.getLong(2), List.of())));
        }, (Object) tenancyIds.toArray(Long[]::new));
        return terms;
    }

    boolean termExists(Long tenancyId, LocalDate validFrom, Long exceptTermId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM longtermrental.rent_terms "
                        + "WHERE tenancy_id = ? AND valid_from = ? AND id <> ?)", Boolean.class, tenancyId,
                Date.valueOf(validFrom), exceptTermId == null ? -1L : exceptTermId));
    }

    Long insertTerm(Long tenancyId, LocalDate validFrom, List<RentItem> items) {
        var id = jdbc.queryForObject("INSERT INTO longtermrental.rent_terms (tenancy_id, valid_from) VALUES (?, ?) "
                + "RETURNING id", Long.class, tenancyId, Date.valueOf(validFrom));
        insertItems(id, items);
        return id;
    }

    void updateTerm(Long termId, LocalDate validFrom, List<RentItem> items) {
        jdbc.update("UPDATE longtermrental.rent_terms SET valid_from = ? WHERE id = ?", Date.valueOf(validFrom), termId);
        jdbc.update("DELETE FROM longtermrental.rent_items WHERE rent_term_id = ?", termId);
        insertItems(termId, items);
    }

    void moveTerm(Long termId, LocalDate validFrom) {
        jdbc.update("UPDATE longtermrental.rent_terms SET valid_from = ? WHERE id = ?", Date.valueOf(validFrom), termId);
    }

    void deleteTerm(Long termId) {
        jdbc.update("DELETE FROM longtermrental.rent_terms WHERE id = ?", termId);
    }

    private void insertItems(Long termId, List<RentItem> items) {
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            jdbc.update("INSERT INTO longtermrental.rent_items (rent_term_id, kind, label, net_amount, position) "
                    + "VALUES (?, ?, ?, ?, ?)", termId, item.kind().name(), item.label(), item.amount(), i);
        }
    }

    private static Date date(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static Tenancy tenancy(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var end = rs.getDate("end_date");
        var received = rs.getDate("deposit_received_on");
        return new Tenancy(rs.getLong("id"), rs.getLong("unit_id"), rs.getString("kind"),
                rs.getDate("start_date").toLocalDate(), end == null ? null : end.toLocalDate(),
                rs.getBigDecimal("deposit_amount"), received == null ? null : received.toLocalDate(),
                rs.getBoolean("tenants_deleted"), rs.getLong("version"));
    }
}
