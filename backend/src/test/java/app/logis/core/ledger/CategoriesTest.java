package app.logis.core.ledger;

import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoriesTest extends LedgerTest {

    private Long create(Actor admin, String name, String kind, Long parentId) throws Exception {
        var body = new HashMap<String, Object>(Map.of("name", name));
        if (kind != null) {
            body.put("kind", kind);
        }
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        var json = postJson("/api/admin/categories", body, admin.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void userCannotManage() throws Exception {
        var user = actor();
        postJson("/api/admin/categories", Map.of("name", "X", "kind", "EXPENSE"), user.session())
                .andExpect(status().isForbidden());
        getWith("/api/categories", user.session()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Wohnen')].children[*].name").isNotEmpty());
    }

    @Test
    void childTakesParentKindAndNoThirdLevel() throws Exception {
        var admin = actor(Role.ADMIN);
        var root = create(admin, "Haus " + System.nanoTime(), "EXPENSE", null);
        var child = create(admin, "Hausgeld", "INCOME", root);
        getWith("/api/categories", admin.session())
                .andExpect(jsonPath("$[?(@.id == " + root + ")].children[0].kind").value("EXPENSE"));
        postJson("/api/admin/categories", Map.of("name", "Zu tief", "parentId", child), admin.session())
                .andExpect(status().isBadRequest());
        postJson("/api/admin/categories", Map.of("name", "hausgeld", "parentId", root), admin.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Eine Kategorie mit diesem Namen existiert bereits"));
    }

    @Test
    void usedCategoryOnlyArchivable() throws Exception {
        var admin = actor(Role.ADMIN);
        var cat = create(admin, "Benutzt " + System.nanoTime(), "EXPENSE", null);
        var account = createAccount(admin);
        addTransaction(admin, account, "-3.00", "2026-10-01", cat);

        mvc.perform(delete("/api/admin/categories/" + cat).with(csrf()).session(admin.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Kategorie wird verwendet und kann nur archiviert werden"));
        mvc.perform(post("/api/admin/categories/" + cat + "/archive").with(csrf()).session(admin.session()))
                .andExpect(jsonPath("$.archived").value(true));
        getWith("/api/categories", admin.session()).andExpect(jsonPath("$[?(@.id == " + cat + ")]").isEmpty());
        getWith("/api/admin/categories", admin.session())
                .andExpect(jsonPath("$[?(@.id == " + cat + ")].archived").value(true));
        mvc.perform(post("/api/admin/categories/" + cat + "/restore").with(csrf()).session(admin.session()))
                .andExpect(jsonPath("$.archived").value(false));

        var unused = create(admin, "Unbenutzt " + System.nanoTime(), "INCOME", null);
        mvc.perform(delete("/api/admin/categories/" + unused).with(csrf()).session(admin.session()))
                .andExpect(status().isNoContent());
    }

    @Test
    void renameAndMoveWithinKind() throws Exception {
        var admin = actor(Role.ADMIN);
        var a = create(admin, "A " + System.nanoTime(), "EXPENSE", null);
        var b = create(admin, "B " + System.nanoTime(), "EXPENSE", null);
        var income = create(admin, "C " + System.nanoTime(), "INCOME", null);
        var child = create(admin, "Kind", null, a);
        mvc.perform(put("/api/admin/categories/" + child).with(csrf()).session(admin.session())
                        .contentType("application/json").content("{\"name\":\"Kind neu\",\"parentId\":" + b + "}"))
                .andExpect(jsonPath("$.parentId").value(b)).andExpect(jsonPath("$.name").value("Kind neu"));
        mvc.perform(put("/api/admin/categories/" + child).with(csrf()).session(admin.session())
                        .contentType("application/json").content("{\"name\":\"Kind neu\",\"parentId\":" + income + "}"))
                .andExpect(status().isBadRequest());
    }
}
