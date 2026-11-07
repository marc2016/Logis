package app.logis.core.modules;

import app.logis.LogisApplication;
import app.logis.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDataRetainedTest {

    @Test
    void dataSurvivesDisablingAndReenabling() {
        var label = "erhalten-" + System.nanoTime();
        try (var ctx = start(true)) {
            ctx.getBean(JdbcTemplate.class).update("INSERT INTO vacationrental.test_record(label) VALUES (?)", label);
            ctx.getBean(JdbcTemplate.class).update("INSERT INTO vacationrental.bookings "
                    + "(unit_id, arrival, departure, adults, external_ref) VALUES (-1, DATE '2026-11-06', "
                    + "DATE '2026-11-08', 2, ?)", label);
        }
        try (var ctx = start(false)) {
            assertThat(ctx.getBeansOfType(ModuleDescriptor.class)).isEmpty();
        }
        try (var ctx = start(true)) {
            assertThat(ctx.getBeansOfType(ModuleDescriptor.class)).hasSize(1);
            var count = ctx.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM vacationrental.test_record WHERE label = ?", Integer.class, label);
            assertThat(count).isEqualTo(1);
            var bookings = ctx.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM vacationrental.bookings WHERE external_ref = ?",
                            Integer.class, label);
            assertThat(bookings).isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext start(boolean enabled) {
        var args = new ArrayList<>(TestPostgres.args());
        args.add("--server.port=0");
        args.add("--logis.modules.vacationrental.enabled=" + enabled);
        return new SpringApplicationBuilder(LogisApplication.class).profiles("test").run(args.toArray(String[]::new));
    }
}
