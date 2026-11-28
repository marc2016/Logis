package app.logis.modules.longtermrental;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rent payments, rent account and arrears (spec rent-payments). All values are invented. */
class RentPaymentsTest extends LongTermRentalTest {

    private Actor anna;
    private long[] house;
    private Long tenancyId;
    private Long giro;

    /** Wednesday, 20 May 2027: March, April and May are due with 800,00 each. */
    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2027-05-20T10:00:00Z"));
        anna = actor();
        house = rentalHouse(anna);
        tenancyId = tenancy(anna, house[1], LocalDate.of(2027, 3, 1), null);
        giro = account(anna, "Giro");
    }

    private String payments() {
        return TENANCIES + "/" + tenancyId + "/payments";
    }

    private org.springframework.test.web.servlet.ResultActions pay(Actor actor, String date, String amount, Long account)
            throws Exception {
        return postJson(payments(), Map.of("date", date, "amount", amount, "accountId", account, "note", "Überweisung"),
                actor.session());
    }

    private Long transactionOf(Long paymentId) {
        return jdbc.queryForObject("SELECT transaction_id FROM longtermrental.rent_payments WHERE id = ?", Long.class,
                paymentId);
    }

    @Test
    void recordChangeAndDeleteWithTheirTransactions() throws Exception {
        var json = pay(anna, "2027-03-03", "800.00", giro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.due").value("2400.00"))
                .andExpect(jsonPath("$.paid").value("800.00"))
                .andExpect(jsonPath("$.arrears").value("1600.00"))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.months", hasSize(3)))
                .andExpect(jsonPath("$.months[0].month").value("2027-05"))
                .andExpect(jsonPath("$.months[0].overdue").value(true))
                .andExpect(jsonPath("$.months[2].month").value("2027-03"))
                .andExpect(jsonPath("$.months[2].open").value("0.00"))
                .andExpect(jsonPath("$.payments[0].accountName").value("Giro"))
                .andExpect(jsonPath("$.payments[0].note").value("Überweisung"))
                .andReturn().getResponse().getContentAsString();
        var paymentId = ((Number) JsonPath.read(json, "$.payments[0].id")).longValue();
        var tx = transactionOf(paymentId);
        var row = jdbc.queryForMap("SELECT t.amount, t.booking_date, t.unit_id, t.counterparty, t.purpose, c.name "
                + "FROM core.transactions t LEFT JOIN core.categories c ON c.id = t.category_id WHERE t.id = ?", tx);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("800.00");
        assertThat(row.get("booking_date").toString()).isEqualTo("2027-03-03");
        assertThat(((Number) row.get("unit_id")).longValue()).isEqualTo(house[1]);
        assertThat(row.get("counterparty")).isEqualTo("Erika Musterfrau");
        assertThat((String) row.get("purpose")).startsWith("Miete Haus").endsWith("· Whg. 1");
        assertThat(row.get("name")).isEqualTo("Mieteinnahmen");

        var change = new HashMap<String, Object>(Map.of("date", "2027-03-04", "amount", "750.00", "accountId", giro,
                "version", 0));
        putJson(payments() + "/" + paymentId, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrears").value("1650.00"));
        assertThat(jdbc.queryForObject("SELECT amount FROM core.transactions WHERE id = ?", BigDecimal.class, tx))
                .isEqualByComparingTo("750.00");
        putJson(payments() + "/" + paymentId, change, anna.session()).andExpect(status().isConflict());

        deleteWith(payments() + "/" + paymentId, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.transactions WHERE id = ?", Integer.class, tx))
                .isZero();
    }

    @Test
    void invalidPaymentsAndAccountsWithoutRights() throws Exception {
        pay(anna, "2027-03-03", "0", giro).andExpect(status().isBadRequest());
        pay(anna, "2027-03-03", "-5.00", giro).andExpect(status().isBadRequest());
        postJson(payments(), Map.of("amount", "800.00", "accountId", giro), anna.session())
                .andExpect(status().isBadRequest());

        var bert = actor();
        share(anna, "unit", house[1], bert, "EDITOR");
        // Bert may edit the unit, but only read the account.
        postJson("/api/shares/account/" + giro, Map.of("userId", bert.id(), "role", "VIEWER"), anna.session())
                .andExpect(status().isOk());
        pay(bert, "2027-03-03", "800.00", giro).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM longtermrental.rent_payments WHERE tenancy_id = ?",
                Integer.class, tenancyId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.transactions WHERE account_id = ?", Integer.class,
                giro)).isZero();
    }

    @Test
    void viewerSeesTheAccountButNoAccountNames() throws Exception {
        pay(anna, "2027-03-03", "900.00", giro).andExpect(status().isCreated());
        var dieter = actor();
        share(anna, "property", house[0], dieter, "VIEWER");
        getWith(TENANCIES + "/" + tenancyId + "/rent-account", dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.payments[0].amount").value("900.00"))
                .andExpect(jsonPath("$.payments[0].accountName").doesNotExist())
                .andExpect(jsonPath("$.payments[0].accountId").doesNotExist())
                .andExpect(jsonPath("$.payments[0].note").doesNotExist());
        pay(dieter, "2027-03-03", "1.00", giro).andExpect(status().isForbidden());
        getWith(TENANCIES + "/" + tenancyId + "/rent-account", actor().session()).andExpect(status().isNotFound());
    }

    @Test
    void arrearsInTheList() throws Exception {
        var paidUp = tenancy(anna, house[2], LocalDate.of(2027, 5, 1), null);
        postJson(TENANCIES + "/" + paidUp + "/payments", Map.of("date", "2027-05-02", "amount", "900.00",
                "accountId", giro), anna.session()).andExpect(status().isCreated());
        pay(anna, "2027-03-03", "800.00", giro).andExpect(status().isCreated());
        getWith(TENANCIES + "?propertyId=" + house[0], anna.session())
                .andExpect(jsonPath("$[?(@.id == " + tenancyId + ")].arrears").value(org.hamcrest.Matchers.contains("1600.00")))
                .andExpect(jsonPath("$[?(@.id == " + tenancyId + ")].overdue").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + paidUp + ")].arrears").value(org.hamcrest.Matchers.contains("0.00")))
                .andExpect(jsonPath("$[?(@.id == " + paidUp + ")].credit").value(org.hamcrest.Matchers.contains("100.00")));
    }

    @Test
    void tenancyWithPaymentsIsNotDeletedAndUnitChangesMoveTheTransactions() throws Exception {
        var json = pay(anna, "2027-03-03", "800.00", giro).andReturn().getResponse().getContentAsString();
        var tx = transactionOf(((Number) JsonPath.read(json, "$.payments[0].id")).longValue());
        deleteWith(TENANCIES + "/" + tenancyId, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Mietverhältnis hat Zahlungen und kann nicht gelöscht werden"));

        var tenancy = JsonPath.parse(getWith(TENANCIES + "/" + tenancyId, anna.session()).andReturn().getResponse()
                .getContentAsString());
        var change = new HashMap<String, Object>(Map.of("unitId", house[2], "startDate", "2027-03-01",
                "tenants", java.util.List.of(tenant("Erika Musterfrau")),
                "version", ((Number) tenancy.read("$.version")).longValue()));
        putJson(TENANCIES + "/" + tenancyId, change, anna.session()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT unit_id FROM core.transactions WHERE id = ?", Long.class, tx))
                .isEqualTo(house[2]);
    }

    @Test
    void paymentsAreIncomeOfThePropertyOnce() throws Exception {
        pay(anna, "2027-04-03", "800.00", giro).andExpect(status().isCreated());
        getWith("/api/properties/" + house[0] + "/finances?year=2027", anna.session())
                .andExpect(jsonPath("$.months[3].income").value("800.00"))
                .andExpect(jsonPath("$.total.income").value("800.00"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].label").value("Miete Erika Musterfrau"));
    }
}
