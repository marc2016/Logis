package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.geo.GeoLocator;
import app.logis.core.users.Role;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Warning mails for sign-ins from a new device or country (spec sign-in-alerts); invented values only. */
@Import(SignInAlertsTest.GeoStub.class)
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class SignInAlertsTest extends IntegrationTest {

    /** Documentation networks: 203.0.113.0/24 is Hamburg, 198.51.100.0/24 Madrid; everything else unknown. */
    @TestConfiguration(proxyBeanMethods = false)
    static class GeoStub {
        @Bean
        @Primary
        GeoLocator testGeoLocator() {
            return ip -> ip == null ? Optional.empty()
                    : ip.startsWith("203.0.113.") ? Optional.of(new GeoLocator.Location("DE", "Deutschland", "Hamburg"))
                    : ip.startsWith("198.51.100.") ? Optional.of(new GeoLocator.Location("ES", "Spanien", "Madrid"))
                    : Optional.empty();
        }
    }

    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SignInMonitor monitor;

    @BeforeEach
    void mailServer() {
        settings(greenMail.getSmtp().getPort());
    }

    private void settings(int port) {
        jdbc.update("DELETE FROM core.smtp_settings");
        jdbc.update("INSERT INTO core.smtp_settings (id, host, port, security, from_address, updated_at) "
                + "VALUES (1, 'localhost', ?, 'NONE', 'logis@example.org', ?)", port, Timestamp.from(Instant.now()));
    }

    private TestUser userWithAddress(String email) {
        var user = createUser(Role.USER, true);
        var entity = users.findById(user.id()).orElseThrow();
        entity.setEmail(email);
        users.save(entity);
        return user;
    }

    private Device device(String userAgent, String ip) {
        var device = new Device(userAgent);
        device.ip = ip;
        return device;
    }

    private MimeMessage[] mailsTo(String address) {
        return Arrays.stream(greenMail.getReceivedMessages()).filter(m -> {
            try {
                return m.getAllRecipients()[0].toString().equals(address);
            } catch (Exception e) {
                return false;
            }
        }).toArray(MimeMessage[]::new);
    }

    /** The mail is sent in the background: wait for it, or make sure none comes. */
    private MimeMessage[] awaitMails(String address, int count) throws Exception {
        greenMail.waitForIncomingEmail(count == 0 ? 1500 : 5000, Math.max(count, 1));
        Thread.sleep(count == 0 ? 0 : 200);
        var mails = mailsTo(address);
        assertThat(mails).hasSize(count);
        return mails;
    }

    @Test
    void firstSignInAndSameBrowserSendNothing_newBrowserSendsOneMail() throws Exception {
        var anna = userWithAddress("anna@example.org");
        var laptop = device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        assertThat(laptop.deviceCookie).isNotBlank();
        awaitMails("anna@example.org", 0);

        // Sign out and in again on the same browser: the device cookie stays.
        assertStatus(laptop.post("/api/auth/logout", Map.of()), 204);
        assertThat(laptop.deviceCookie).isNotBlank();
        laptop.signIn(anna, false);
        awaitMails("anna@example.org", 0);

        device(CHROME_ANDROID, "203.0.113.8").signIn(anna, false);
        var mail = awaitMails("anna@example.org", 1)[0];
        assertThat(mail.getSubject()).isEqualTo("Neue Anmeldung bei Logis");
        var text = (String) mail.getContent();
        assertThat(text).contains("Chrome auf Android", "Hamburg, Deutschland", "Alle anderen Geräte abmelden",
                "Passwort").containsPattern("\\d{2}\\.\\d{2}\\.\\d{4}, \\d{2}:\\d{2} Uhr").doesNotContain("203.0.113");
    }

    @Test
    void newCountryOnAKnownDevice() throws Exception {
        var anna = userWithAddress("anna.reise@example.org");
        var laptop = device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        laptop.ip = "198.51.100.20";
        laptop.signIn(anna, false);
        var text = (String) awaitMails("anna.reise@example.org", 1)[0].getContent();
        assertThat(text).contains("Firefox auf Linux", "Madrid, Spanien");

        // Back home: Germany is known.
        laptop.ip = "203.0.113.9";
        laptop.signIn(anna, false);
        awaitMails("anna.reise@example.org", 1);
    }

    @Test
    void localAddressesAreNeverANewLocation() throws Exception {
        var anna = userWithAddress("anna.lokal@example.org");
        var laptop = device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        laptop.ip = "192.168.1.20";
        laptop.signIn(anna, false);
        awaitMails("anna.lokal@example.org", 0);
        assertThat(jdbc.queryForList("SELECT country FROM core.sign_ins WHERE user_id = ? ORDER BY at, id",
                String.class, anna.id())).containsExactly("DE", null);
    }

    @Test
    void noAddressOrNoServerSendsNothing() throws Exception {
        var bert = createUser(Role.USER, true);
        device(FIREFOX_LINUX, "203.0.113.7").signIn(bert, false);
        device(CHROME_ANDROID, "198.51.100.1").signIn(bert, false);

        jdbc.update("DELETE FROM core.smtp_settings");
        var anna = userWithAddress("anna.ohne@example.org");
        device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        device(CHROME_ANDROID, "198.51.100.1").signIn(anna, false);
        greenMail.waitForIncomingEmail(1500, 1);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void unreachableMailServerDoesNotFailTheSignIn() throws Exception {
        var anna = userWithAddress("anna.down@example.org");
        device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        settings(1);
        // signIn asserts that every step succeeds.
        var phone = device(CHROME_ANDROID, "198.51.100.1").signIn(anna, false);
        assertStatus(phone.get("/api/modules"), 200);
    }

    @Test
    void passkeySignInsAreWatchedToo() throws Exception {
        var anna = userWithAddress("anna.passkey@example.org");
        var browser = device(FIREFOX_LINUX, "203.0.113.7").signIn(anna, false);
        var authenticator = new SoftwareAuthenticator("https://logis.example.org");
        var options = json.readTree(browser.post("/api/account/passkeys/registration/options",
                Map.of("password", anna.password())).getResponse().getContentAsString());
        assertStatus(browser.post("/api/account/passkeys/registration",
                Map.of("name", "Laptop", "credential", authenticator.register(options))), 200);

        var phone = device(CHROME_ANDROID, "203.0.113.8");
        var signInOptions = json.readTree(phone.post("/api/auth/passkey/options", Map.of()).getResponse()
                .getContentAsString());
        assertStatus(phone.post("/api/auth/passkey", Map.of("credential", authenticator.signIn(signInOptions, true))),
                200);
        assertThat(phone.deviceCookie).isNotBlank();
        awaitMails("anna.passkey@example.org", 1);
    }

    @Test
    void historyHasNoAddressesAndOldEntriesGo() throws Exception {
        var anna = userWithAddress("anna.historie@example.org");
        device(FIREFOX_LINUX, "203.0.113.77").signIn(anna, false);
        var row = jdbc.queryForMap("SELECT * FROM core.sign_ins WHERE user_id = ?", anna.id());
        assertThat(row.keySet()).containsExactlyInAnyOrder("id", "user_id", "at", "device_hash", "browser", "os",
                "country", "city");
        assertThat(row.values().stream().map(String::valueOf)).noneMatch(v -> v.contains("203.0.113"));
        assertThat(row).containsEntry("browser", "Firefox").containsEntry("os", "Linux")
                .containsEntry("country", "DE").containsEntry("city", "Hamburg");

        jdbc.update("UPDATE core.sign_ins SET at = at - interval '181 days' WHERE user_id = ?", anna.id());
        device(FIREFOX_LINUX, "203.0.113.77").signIn(anna, false);
        clock.advance(Duration.ofSeconds(1));
        monitor.deleteOld();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.sign_ins WHERE user_id = ?", Long.class, anna.id()))
                .isEqualTo(1);
    }
}
