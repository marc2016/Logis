package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Country check and location of remembered devices (design D6 of sign-in-alerts); documentation networks only. */
@Import(SignInAlertsTest.GeoStub.class)
class RememberedDeviceLocationTest extends IntegrationTest {

    private Device remembered(TestUser user, String ip) throws Exception {
        var device = new Device(FIREFOX_LINUX);
        device.ip = ip;
        return device.signIn(user, true);
    }

    @Test
    void anotherCountryIsRejected() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = remembered(anna, "203.0.113.7");

        laptop.loseSession();
        laptop.ip = "198.51.100.7";
        assertStatus(laptop.get("/api/modules"), 401);
        assertThat(laptop.cookie).isNull();

        // Forgotten: back home it has to sign in again too.
        laptop.ip = "203.0.113.7";
        assertStatus(laptop.get("/api/modules"), 401);
    }

    @Test
    void unknownCountriesAreNotCompared() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = remembered(anna, "192.168.1.20");
        laptop.loseSession();
        laptop.ip = "198.51.100.7";
        assertStatus(laptop.get("/api/modules"), 200);

        // Now known as Spain; an unknown address afterwards is not compared either.
        laptop.loseSession();
        laptop.ip = "10.0.0.5";
        assertStatus(laptop.get("/api/modules"), 200);
    }

    @Test
    void sameCountryOtherCity() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = remembered(anna, "203.0.113.7");
        laptop.loseSession();
        laptop.ip = "203.0.113.200";
        assertStatus(laptop.get("/api/modules"), 200);
    }

    @Test
    void deviceListShowsTheLocation() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = remembered(anna, "203.0.113.7");
        remembered(anna, "192.168.1.20");
        var list = json.readTree(laptop.get("/api/account/devices").getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        for (var entry : list) {
            if (entry.get("current").asBoolean()) {
                assertThat(entry.get("city").asText()).isEqualTo("Hamburg");
                assertThat(entry.get("countryName").asText()).isEqualTo("Deutschland");
            } else {
                assertThat(entry.get("city").isNull()).isTrue();
                assertThat(entry.get("countryName").isNull()).isTrue();
            }
        }
    }
}
