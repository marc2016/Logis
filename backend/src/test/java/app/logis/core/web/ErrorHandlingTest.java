package app.logis.core.web;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ErrorHandlingTest.BoomController.class)
class ErrorHandlingTest extends IntegrationTest {

    @RestController
    static class BoomController {
        @GetMapping("/api/test/boom")
        String boom() {
            throw new IllegalStateException("geheimes Detail aus org.hibernate Version 6.6");
        }
    }

    @Test
    void unexpectedErrorRevealsNoDetails() throws Exception {
        mvc.perform(get("/api/test/boom").with(user("u").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ein unerwarteter Fehler ist aufgetreten."))
                .andExpect(content().string(not(containsString("geheimes"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("trace"))))
                .andExpect(content().string(not(containsString("6.6"))));
    }

    @Test
    void unknownApiPathGives404WithoutDetails() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(user("u").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("NoResourceFound"))))
                .andExpect(content().string(not(containsString("static resource"))));
    }
}
