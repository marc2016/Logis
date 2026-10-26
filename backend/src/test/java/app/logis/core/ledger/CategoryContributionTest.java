package app.logis.core.ledger;

import app.logis.core.modules.CategoryContribution;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Beitrag eines Moduls ("vacationrental", das in den Tests aktiv ist) und eines inaktiven Moduls.
 */
@Import(CategoryContributionTest.Contributions.class)
@TestPropertySource(properties = "logis.test.context=category-contributions")
class CategoryContributionTest extends LedgerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Contributions {
        @Bean
        CategoryContribution vrRoot() {
            return new CategoryContribution("vacationrental", "test.vr.root", false, "Ferien-Testkosten", null);
        }

        @Bean
        CategoryContribution vrChild() {
            return new CategoryContribution("vacationrental", "test.vr.cleaning", false, "Test-Reinigung",
                    "test.vr.root");
        }

        @Bean
        CategoryContribution inactive() {
            return new CategoryContribution("gibtsnicht", "test.inactive", true, "Inaktives Modul", null);
        }
    }

    private Long idOf(String key) {
        return jdbc.queryForObject("SELECT id FROM core.categories WHERE module_key = ?", Long.class, key);
    }

    @Test
    void contributionsAreCreatedOnceAndProtected() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.categories WHERE module_key LIKE 'test.%'",
                Integer.class)).isEqualTo(3);
        var child = idOf("test.vr.cleaning");
        assertThat(jdbc.queryForObject("SELECT parent_id FROM core.categories WHERE id = ?", Long.class, child))
                .isEqualTo(idOf("test.vr.root"));

        var admin = actor(Role.ADMIN);
        mvc.perform(put("/api/admin/categories/" + child).with(csrf()).session(admin.session())
                        .contentType("application/json").content("{\"name\":\"Anders\",\"parentId\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Kategorie wird von einem Modul verwaltet"));
        mvc.perform(delete("/api/admin/categories/" + child).with(csrf()).session(admin.session()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/categories/" + child + "/archive").with(csrf()).session(admin.session()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/categories/" + child + "/restore").with(csrf()).session(admin.session()))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveModuleCategoriesAreHiddenFromSelection() throws Exception {
        var user = actor();
        getWith("/api/categories", user.session())
                .andExpect(jsonPath("$[?(@.name == 'Ferien-Testkosten')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Inaktives Modul')]").isEmpty());
    }

    @Test
    void rerunDoesNotDuplicate() throws Exception {
        var sync = new CategoryContributionSync(
                applicationContext.getBean(CategoryRepository.class),
                applicationContext.getBeansOfType(CategoryContribution.class).values().stream().toList());
        tx.executeWithoutResult(s -> sync.run(null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.categories WHERE module_key LIKE 'test.%'",
                Integer.class)).isEqualTo(3);
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext applicationContext;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.support.TransactionTemplate tx;
}
