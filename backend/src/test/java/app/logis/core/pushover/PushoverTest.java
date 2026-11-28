package app.logis.core.pushover;

import app.logis.IntegrationTest;
import app.logis.core.notifications.Notification;
import app.logis.core.notifications.Notifications;
import app.logis.core.notifications.TestChannels;
import app.logis.core.pushover.PushoverClient.PushoverException.Kind;
import app.logis.core.users.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The channel „Pushover“ against a fake of the service (spec pushover); token and keys are invented. */
@Import({FakePushover.class, TestChannels.class})
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class PushoverTest extends IntegrationTest {

    private static final String CHANNEL = "$.channels[?(@.id == 'pushover')]";
    private static final String OTHER_KEY = "userkey11111111111111111111111";
    private static final Notification NOTE = new Notification("Neue Buchung zu prüfen",
            "Haus Waldeck, 12.07.2027 – 19.07.2027", "Haus Waldeck\nGast: Erika Musterfrau", "/buchungen/7");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FakePushover.Client pushover;
    @Autowired
    Notifications notifications;

    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM core.pushover_settings");
        pushover.reset();
        pushover.userKeys.add(OTHER_KEY);
        admin = login(createUser(Role.ADMIN, true));
    }

    private ResultActions putJson(String url, Object body, MockHttpSession session) throws Exception {
        return mvc.perform(put(url).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private void storeToken() throws Exception {
        putJson("/api/admin/pushover", Map.of("token", FakePushover.TOKEN), admin).andExpect(status().isOk());
    }

    /** A user with a stored key who switched Pushover on for the test event. */
    private TestUser userWithKey(String key) throws Exception {
        var user = createUser(Role.USER, true);
        putJson("/api/account/pushover", Map.of("userKey", key), login(user)).andExpect(status().isOk());
        jdbc.update("INSERT INTO core.notification_settings (user_id, event_type, channel, enabled, updated_at) "
                + "VALUES (?, ?, 'pushover', true, now())", user.id(), TestChannels.TYPE);
        return user;
    }

    // --- Application token ---------------------------------------------------------------------

    @Test
    void theAdminStoresTheTokenAndItIsNeverReturned() throws Exception {
        getWith("/api/admin/pushover", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        putJson("/api/admin/pushover", Map.of("token", " " + FakePushover.TOKEN + " "), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(content().string(not(containsString(FakePushover.TOKEN))));
        getWith("/api/admin/pushover", admin).andExpect(jsonPath("$.configured").value(true))
                .andExpect(content().string(not(containsString(FakePushover.TOKEN))));
        // Encrypted at rest.
        assertThat(jdbc.queryForObject("SELECT app_token_enc FROM core.pushover_settings", String.class))
                .doesNotContain(FakePushover.TOKEN);
    }

    @Test
    void ordinaryUsersNeitherSeeNorChangeTheConfiguration() throws Exception {
        var anna = login(createUser(Role.USER, true));
        getWith("/api/admin/pushover", anna).andExpect(status().isForbidden());
        putJson("/api/admin/pushover", Map.of("token", FakePushover.TOKEN), anna).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/pushover").with(csrf()).session(anna)).andExpect(status().isForbidden());
    }

    @Test
    void aRejectedOrUncheckableTokenKeepsThePreviousOne() throws Exception {
        storeToken();
        var stored = jdbc.queryForObject("SELECT app_token_enc FROM core.pushover_settings", String.class);

        putJson("/api/admin/pushover", Map.of("token", "wrongtoken00000000000000000000"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Pushover-Token ist ungültig"));
        pushover.unreachable = true;
        putJson("/api/admin/pushover", Map.of("token", FakePushover.TOKEN), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pushover ist nicht erreichbar"));
        putJson("/api/admin/pushover", Map.of("token", ""), admin).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT app_token_enc FROM core.pushover_settings", String.class))
                .isEqualTo(stored);
    }

    @Test
    void removingTheTokenStopsSendingAndKeepsKeysAndSwitches() throws Exception {
        storeToken();
        var anna = userWithKey(FakePushover.KEY);

        mvc.perform(delete("/api/admin/pushover").with(csrf()).session(admin)).andExpect(status().isNoContent());
        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        pushover.await(FakePushover.KEY, 0);
        var session = login(anna);
        getWith("/api/account/pushover", session).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.hasKey").value(true));
        getWith("/api/notifications", session).andExpect(jsonPath(CHANNEL + ".usable").value(false))
                .andExpect(jsonPath(CHANNEL + ".hint").value("Pushover ist noch nicht eingerichtet"))
                .andExpect(jsonPath("$.types[?(@.key == '" + TestChannels.TYPE + "')].channels.pushover.enabled")
                        .value(true));
    }

    // --- Own key -------------------------------------------------------------------------------

    @Test
    void aUserStoresRemovesAndNeverReadsTheirOwnKey() throws Exception {
        var user = createUser(Role.USER, true);
        var anna = login(user);
        getWith("/api/account/pushover", anna).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.hasKey").value(false));
        putJson("/api/account/pushover", Map.of("userKey", FakePushover.KEY), anna)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pushover ist noch nicht eingerichtet"));
        storeToken();
        getWith("/api/notifications", anna).andExpect(jsonPath(CHANNEL + ".label").value("Pushover"))
                .andExpect(jsonPath(CHANNEL + ".usable").value(false))
                .andExpect(jsonPath(CHANNEL + ".hint").value("Kein Pushover-Schlüssel hinterlegt"));

        putJson("/api/account/pushover", Map.of("userKey", "unknownkey00000000000000000000"), anna)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Pushover-Schlüssel ist ungültig"));
        getWith("/api/account/pushover", anna).andExpect(jsonPath("$.hasKey").value(false));

        putJson("/api/account/pushover", Map.of("userKey", FakePushover.KEY), anna).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.hasKey").value(true))
                .andExpect(jsonPath("$.invalid").value(false))
                .andExpect(content().string(not(containsString(FakePushover.KEY))));
        getWith("/api/notifications", anna).andExpect(jsonPath(CHANNEL + ".usable").value(true));
        assertThat(jdbc.queryForObject("SELECT user_key_enc FROM core.pushover_user_keys WHERE user_id = ?",
                String.class, user.id())).doesNotContain(FakePushover.KEY);

        // Another user's key is untouched by mine, and mine by theirs.
        var bert = login(createUser(Role.USER, true));
        getWith("/api/account/pushover", bert).andExpect(jsonPath("$.hasKey").value(false));
        mvc.perform(delete("/api/account/pushover").with(csrf()).session(bert)).andExpect(status().isNoContent());
        getWith("/api/account/pushover", anna).andExpect(jsonPath("$.hasKey").value(true));

        mvc.perform(delete("/api/account/pushover").with(csrf()).session(anna)).andExpect(status().isNoContent());
        getWith("/api/account/pushover", anna).andExpect(jsonPath("$.hasKey").value(false));
    }

    @Test
    void theTestNotificationGoesToTheOwnKeyAtOnceAndReportsAFailure() throws Exception {
        storeToken();
        var anna = login(userWithKey(FakePushover.KEY));

        postJson("/api/account/pushover/test", Map.of(), anna).andExpect(status().isNoContent());
        assertThat(pushover.sentTo(FakePushover.KEY)).singleElement().satisfies(message -> {
            assertThat(message.title()).isEqualTo("Testnachricht von Logis");
            assertThat(message.link()).isNull();
        });

        pushover.unreachable = true;
        postJson("/api/account/pushover/test", Map.of(), anna).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Testnachricht konnte nicht gesendet werden"));
        // Without a key there is nothing to test.
        postJson("/api/account/pushover/test", Map.of(), login(createUser(Role.USER, true)))
                .andExpect(status().isBadRequest());
    }

    // --- Sending -------------------------------------------------------------------------------

    @Test
    void onlyTokenKeyTitleShortTextAndLinkLeaveTheSystem() throws Exception {
        storeToken();
        var anna = userWithKey(FakePushover.KEY);
        var entity = users.findById(anna.id()).orElseThrow();
        entity.setEmail("anna@example.org");
        users.save(entity);
        var bert = userWithKey(OTHER_KEY);

        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        assertThat(pushover.await(FakePushover.KEY, 1).get(0)).isEqualTo(new FakePushover.Message(
                FakePushover.TOKEN, FakePushover.KEY, "Neue Buchung zu prüfen",
                "Haus Waldeck, 12.07.2027 – 19.07.2027", "https://logis.example.org/buchungen/7"));
        assertThat(pushover.messages.toString()).doesNotContain("Musterfrau").doesNotContain("anna@example.org")
                .doesNotContain(anna.username());
        pushover.await(OTHER_KEY, 0);
        assertThat(bert.id()).isNotEqualTo(anna.id());
    }

    @Test
    void aKeyPushoverNoLongerAcceptsIsNotUsedAgainUntilTheUserStoresOne() throws Exception {
        storeToken();
        var anna = userWithKey(FakePushover.KEY);
        var session = login(anna);
        // The account behind the key is gone.
        pushover.userKeys.remove(FakePushover.KEY);

        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        var deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && jdbc.queryForObject("SELECT invalid_since IS NULL FROM "
                + "core.pushover_user_keys WHERE user_id = ?", Boolean.class, anna.id())) {
            Thread.sleep(25);
        }
        getWith("/api/account/pushover", session).andExpect(jsonPath("$.hasKey").value(true))
                .andExpect(jsonPath("$.invalid").value(true));
        getWith("/api/notifications", session).andExpect(jsonPath(CHANNEL + ".usable").value(false))
                .andExpect(jsonPath(CHANNEL + ".hint").value("Der Pushover-Schlüssel ist nicht mehr gültig"));
        // Later events are not sent to that key, even if it worked again.
        pushover.userKeys.add(FakePushover.KEY);
        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);
        pushover.await(FakePushover.KEY, 0);

        putJson("/api/account/pushover", Map.of("userKey", FakePushover.KEY), session).andExpect(status().isOk())
                .andExpect(jsonPath("$.invalid").value(false));
        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);
        pushover.await(FakePushover.KEY, 1);
    }

    @Test
    void temporaryFailuresAndTheMonthlyLimitLeaveTheKeyValid() throws Exception {
        storeToken();
        var anna = userWithKey(FakePushover.KEY);

        for (var kind : new Kind[] {Kind.UNAVAILABLE, Kind.LIMIT_REACHED, Kind.REJECTED}) {
            pushover.failSendWith = kind;
            notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);
        }
        pushover.await(FakePushover.KEY, 0);
        pushover.failSendWith = null;
        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        // Nothing is sent again, and the next event goes out as usual.
        pushover.await(FakePushover.KEY, 1);
        assertThat(jdbc.queryForObject("SELECT invalid_since IS NULL FROM core.pushover_user_keys WHERE user_id = ?",
                Boolean.class, anna.id())).isTrue();
    }
}
