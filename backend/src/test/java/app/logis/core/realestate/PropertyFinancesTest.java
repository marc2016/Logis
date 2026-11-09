package app.logis.core.realestate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(PropertyFinancesTest.StubSourceConfig.class)
class PropertyFinancesTest extends RealestateTest {

    static final List<FinanceItem> CONTRIBUTED = new CopyOnWriteArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class StubSourceConfig {
        @Bean
        FinanceSource stubSource() {
            return (unitIds, from, to) -> CONTRIBUTED.stream()
                    .filter(i -> unitIds.contains(i.unitId()))
                    .filter(i -> !i.date().isBefore(from) && !i.date().isAfter(to))
                    .toList();
        }
    }

    private static void contribute(Long unitId, String date, FinanceItem.Kind kind, String label, String amount) {
        CONTRIBUTED.add(new FinanceItem(LocalDate.parse(date), unitId, kind, label, new BigDecimal(amount),
                "test.statement", 1L));
    }

    private void cost(Actor actor, Long propertyId, Long unitId, String date, String amount) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("date", date, "amount", amount));
        if (unitId != null) {
            body.put("unitId", unitId);
        }
        postJson("/api/properties/" + propertyId + "/costs", body, actor.session()).andExpect(status().isCreated());
    }

    @Test
    void mergesCostsAndContributedItems() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var unit = unitIds(property).getFirst();
        contribute(unit, "2026-11-30", FinanceItem.Kind.INCOME, "Buchung 250905", "248.58");
        contribute(unit, "2026-11-30", FinanceItem.Kind.EXPENSE, "Provision", "37.29");
        contribute(unit, "2026-11-30", FinanceItem.Kind.EXPENSE, "Reinigung", "60.00");
        contribute(unit, "2025-11-30", FinanceItem.Kind.INCOME, "Vorjahr", "999.00");
        cost(anna, property, null, "2026-11-12", "320.00");

        getWith("/api/properties/" + property + "/finances?year=2026", anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.months[10].income").value("248.58"))
                .andExpect(jsonPath("$.months[10].expenses").value("417.29"))
                .andExpect(jsonPath("$.months[10].result").value("-168.71"))
                .andExpect(jsonPath("$.total.result").value("-168.71"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].date").value("2026-11-30"))
                .andExpect(jsonPath("$.items[?(@.label == 'Kosten')].editable").value(true))
                .andExpect(jsonPath("$.items[?(@.label == 'Provision')].editable").value(false))
                .andExpect(jsonPath("$.canRecord").value(true));

        getWith("/api/properties/" + property + "/finances?year=2026&kind=INCOME&sort=amount,asc", anna.session())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total.expenses").value("417.29"));
    }

    @Test
    void attributedTransactionsDoNotCount() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var account = account(anna, "Giro");
        transaction(anna, Map.of("accountId", account, "amount", "-500.00", "bookingDate", "2026-05-02",
                "propertyId", property));

        getWith("/api/properties/" + property + "/finances?year=2026", anna.session())
                .andExpect(jsonPath("$.total.expenses").value("0.00"))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void visibilityFollowsPropertyAndUnitRoles() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        cost(anna, ids[0], null, "2026-03-01", "100.00");
        cost(anna, ids[0], ids[1], "2026-03-02", "10.00");
        cost(anna, ids[0], ids[2], "2026-03-03", "1.00");
        contribute(ids[2], "2026-03-04", FinanceItem.Kind.INCOME, "Buchung", "50.00");
        var url = "/api/properties/" + ids[0] + "/finances?year=2026";

        getWith(url, carla.session())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total.income").value("50.00"))
                .andExpect(jsonPath("$.total.expenses").value("1.00"))
                .andExpect(jsonPath("$.canRecord").value(true));
        getWith(url, dieter.session())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.total.expenses").value("111.00"))
                .andExpect(jsonPath("$.canRecord").value(false));
        getWith(url + "&unitId=" + ids[1], dieter.session())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total.expenses").value("10.00"));
        getWith(url + "&unitId=" + ids[1], carla.session()).andExpect(status().isNotFound());
        getWith(url, bert.session()).andExpect(status().isNotFound());
    }
}
