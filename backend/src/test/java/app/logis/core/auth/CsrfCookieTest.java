package app.logis.core.auth;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separate context: {@code with(csrf())} in other tests replaces the token repository permanently.
 */
@TestPropertySource(properties = "logis.test.context=csrf-cookie")
class CsrfCookieTest extends IntegrationTest {

    @Test
    void csrfCookieIsReadableForSpa() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
