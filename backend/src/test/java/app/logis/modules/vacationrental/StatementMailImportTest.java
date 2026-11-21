package app.logis.modules.vacationrental;

import app.logis.modules.vacationrental.StatementPdfs.Row;
import app.logis.modules.vacationrental.StatementPdfs.Spec;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Flags;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Statement mails (spec booking-mails, „Statement mails“ and „Fewo & Meer statement PDF“). Mails and PDFs are built
 * from invented values; no real statement is used.
 */
class StatementMailImportTest extends VacationRentalTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    /** AI is "enabled" with a stub, so that a call would be seen. */
    @MockitoBean
    AiExtractor ai;
    @Autowired
    app.logis.core.mail.Mails imports;
    @Autowired
    UnitMappingRepository mappings;

    private static final String OBJECT = "Haus Düne - Beispielort";

    private GreenMailUser mailUser;
    private Actor anna;
    private Long unit;
    private int messageNumber;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        anna = actor();
        unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                "username", "anna", "password", "geheim", "folder", "INBOX", "useTls", false,
                "cutoffDate", "2026-11-01", "enabled", true));
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(anna.session()).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
    }

    @AfterEach
    void neverTheAi() {
        verifyNoInteractions(ai);
    }

    // --- Helpers -------------------------------------------------------------------------------

    private static MimeBodyPart pdfPart(String name, byte[] pdf) throws Exception {
        var part = new MimeBodyPart();
        part.setContent(pdf, "application/pdf");
        part.setFileName(name);
        part.setDisposition("attachment");
        return part;
    }

    private void deliver(byte[] raw) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
        message.setHeader("Message-ID", "<statement-" + (++messageNumber) + "-" + anna.id() + "@example.org>");
        greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                .appendMessage(message, new Flags(), Date.from(Instant.parse("2027-04-04T09:00:00Z")));
        imports.pollNow(anna.id());
    }

    private static byte[] fewoMail(String month, byte[] pdf) throws Exception {
        var html = "<p>Sehr geehrte(r) Beispiel GbR,</p><p>anbei erhalten Sie die Abrechnung " + month
                + " für Ihr Objekt.</p>";
        return pdf == null
                ? StatementMailRulesTest.raw(StatementMailRulesTest.FEWO, "Ihre Abrechnung für " + month + " - " + OBJECT,
                        html)
                : StatementMailRulesTest.raw(StatementMailRulesTest.FEWO, "Ihre Abrechnung für " + month + " - " + OBJECT,
                        html, pdfPart("Beispiel GbR-Haus Düne.pdf", pdf));
    }

    private static byte[] notice(String from, String to, String invoice) throws Exception {
        return notice(StatementMailRulesTest.NOTICE_FROM, from, to, invoice);
    }

    private static byte[] notice(String sender, String from, String to, String invoice) throws Exception {
        return StatementMailRulesTest.raw(sender, "Ihre Abrechnung: " + invoice,
                StatementMailRulesTest.noticeText(from, to, invoice),
                pdfPart("Rechnung " + invoice + ".pdf", StatementPdfs.text("Eigentümerabrechnung", invoice)));
    }

    private List<Map<String, Object>> inbox() throws Exception {
        return JsonPath.read(getWith("/api/mail/inbox", anna.session()).andReturn().getResponse()
                .getContentAsString(), "$");
    }

    private Map<String, Object> item(String subjectPart) throws Exception {
        return inbox().stream().filter(m -> String.valueOf(m.get("subject")).contains(subjectPart)).findFirst()
                .orElseThrow();
    }

    private Long linkId(Map<String, Object> item) {
        return ((Number) item.get("linkId")).longValue();
    }

    private void assign(String subjectPart) throws Exception {
        var id = ((Number) item(subjectPart).get("id")).longValue();
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
    }

    private void mapFewo() throws Exception {
        var agency = jdbc.queryForObject("SELECT id FROM vacationrental.agencies WHERE name = 'Fewo & Meer'",
                Long.class);
        mappings.save(new UnitMapping(anna.id(), agency, OBJECT, unit));
    }

    private Long guestBooking(String arrival, String departure, String rent) throws Exception {
        var body = stay(unit, arrival, departure);
        if (rent != null) {
            body.put("expectedRent", rent);
        }
        return id(book(anna, body).andExpect(status().isCreated()));
    }

    private Long statementOf(Map<String, Object> item) {
        assertThat(item.get("linkType")).isEqualTo("vacationrental.statement");
        return linkId(item);
    }

    // --- Fewo & Meer ---------------------------------------------------------------------------

    @Test
    void fewoStatementWaitsForTheUnitAndSettlesItsRows() throws Exception {
        var erika = guestBooking("2027-03-05", "2027-03-12", "790.00");
        var otto = guestBooking("2027-02-26", "2027-03-03", "1340.50");
        var max = guestBooking("2027-03-14", "2027-03-18", null);
        var paul = guestBooking("2027-03-24", "2027-03-26", null);
        var february = id(postJson("/api/vacationrental/statements", Map.of("unitId", unit, "provider", "Nord",
                "periodFrom", "2027-02-01", "periodTo", "2027-02-28", "statementDate", "2027-03-03"),
                anna.session()));
        putJson("/api/vacationrental/statements/" + february + "/lines/" + paul, Map.of("gross", "100.00"),
                anna.session()).andExpect(status().isOk());
        var ownerUse = stay(unit, "2027-03-27", "2027-03-29");
        ownerUse.put("kind", "OWNER");
        book(anna, ownerUse).andExpect(status().isCreated());

        var pdf = StatementPdfs.pdf(Spec.of(OBJECT, "4711-2027-03", "2027-04-03", "März 2027",
                Row.of("Erika Musterfrau", "2027-03-05", "2027-03-12", "700.00", "90.00", "105.04"),
                Row.of("Max Mustermann", "2027-03-15", "2027-03-18", "300.00", "90.00", "45.00"),
                Row.of("Otto Beispiel", "2027-02-26", "2027-03-03", "1250.50", "90.00", "187.58"),
                Row.of("Max Mustermann", "2027-03-14", "2027-03-15", "80.00", "0.00", "12.00").asExtension(),
                Row.of("Nina Neu", "2027-03-20", "2027-03-22", "200.00", "90.00", "30.00"),
                Row.of("Paul Probe", "2027-03-24", "2027-03-26", "180.00", "90.00", "27.00"),
                Row.of("Rita Rand", "2027-03-27", "2027-03-29", "150.00", "90.00", "22.50")));
        deliver(fewoMail("März 2027", pdf));

        var waiting = item("Ihre Abrechnung für März 2027");
        assertThat(waiting.get("result")).isEqualTo("UNASSIGNED_UNIT");
        assertThat(waiting.get("kind")).isEqualTo("STATEMENT");
        assertThat(waiting.get("subjectName")).isEqualTo(OBJECT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.statements WHERE unit_id = ?",
                Integer.class, unit)).isEqualTo(1);

        assign("Ihre Abrechnung für März 2027");
        var created = item("Ihre Abrechnung für März 2027");
        assertThat(created.get("result")).isEqualTo("STATEMENT_CREATED");
        assertThat(created.get("resultDetail")).isNull();
        assertThat(created.get("source")).isEqualTo("Fewo & Meer");
        assertThat(created.get("reference")).isEqualTo("4711-2027-03");
        assertThat(created.get("parsedBy")).isEqualTo("RULE");
        var statement = statementOf(created);

        getWith("/api/vacationrental/statements/" + statement, anna.session())
                .andExpect(jsonPath("$.provider").value("Fewo & Meer"))
                .andExpect(jsonPath("$.periodFrom").value("2027-03-01"))
                .andExpect(jsonPath("$.periodTo").value("2027-03-31"))
                .andExpect(jsonPath("$.statementDate").value("2027-04-03"))
                .andExpect(jsonPath("$.invoiceNumber").value("4711-2027-03"))
                .andExpect(jsonPath("$.lines", hasSize(3)))
                // Sorted by arrival: Otto (previous month), Erika, Max (extension merged).
                .andExpect(jsonPath("$.lines[0].booking.id").value(otto))
                .andExpect(jsonPath("$.lines[0].gross").value("1340.50"))
                .andExpect(jsonPath("$.lines[1].booking.id").value(erika))
                .andExpect(jsonPath("$.lines[1].gross").value("790.00"))
                .andExpect(jsonPath("$.lines[1].commission").value("125.00"))
                .andExpect(jsonPath("$.lines[1].cleaning").value("0.00"))
                .andExpect(jsonPath("$.lines[1].net").value("665.00"))
                .andExpect(jsonPath("$.lines[2].booking.id").value(max))
                .andExpect(jsonPath("$.lines[2].gross").value("470.00"))
                .andExpect(jsonPath("$.lines[2].commission").value("67.83"))
                .andExpect(jsonPath("$.openLineCount").value(3))
                .andExpect(jsonPath("$.openLines[0].guestName").value("Nina Neu"))
                .andExpect(jsonPath("$.openLines[0].note").doesNotExist())
                .andExpect(jsonPath("$.openLines[1].guestName").value("Paul Probe"))
                .andExpect(jsonPath("$.openLines[1].note").value("bereits abgerechnet"))
                .andExpect(jsonPath("$.openLines[2].guestName").value("Rita Rand"))
                .andExpect(jsonPath("$.openLines[2].gross").value("240.00"))
                .andExpect(jsonPath("$.toReview").value(true));
        // The stated balance covers all rows, the payout only the settled ones.
        getWith("/api/vacationrental/statements/" + statement, anna.session())
                .andExpect(jsonPath("$.statedPayout").value("2889.84"))
                .andExpect(jsonPath("$.payout").value("2184.45"));
        // The other statement is unchanged.
        getWith("/api/vacationrental/statements/" + february, anna.session())
                .andExpect(jsonPath("$.lines[0].booking.id").value(paul))
                .andExpect(jsonPath("$.lines[0].gross").value("100.00"));
        getWith("/api/documents?ownerType=vacationrental.statement&ownerId=" + statement, anna.session())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName").value("Beispiel GbR-Haus Düne.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"));

        // The same invoice again: no second statement.
        deliver(fewoMail("März 2027", pdf));
        var again = inbox().stream().filter(m -> "ATTACHED".equals(m.get("result"))).findFirst().orElseThrow();
        assertThat(again.get("resultDetail")).isEqualTo("Abrechnung bereits erfasst");
        assertThat(statementOf(again)).isEqualTo(statement);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.statements WHERE unit_id = ?",
                Integer.class, unit)).isEqualTo(2);
    }

    @Test
    void extensionNotTakenOverStaysOpen() throws Exception {
        deliver(fewoMail("Juni 2027", null)); // creates the agency
        mapFewo();
        var stay = guestBooking("2027-06-15", "2027-06-18", null);
        deliver(fewoMail("Mai 2027", StatementPdfs.pdf(Spec.of(OBJECT, "4712-2027-05", "2027-06-03", "Mai 2027",
                Row.of("Max Mustermann", "2027-06-14", "2027-06-15", "80.00", "0.00", "12.00").asExtension(),
                Row.of("Max Mustermann", "2027-06-15", "2027-06-18", "300.00", "90.00", "45.00")))));
        var statement = statementOf(item("Ihre Abrechnung für Mai 2027"));
        getWith("/api/vacationrental/statements/" + statement, anna.session())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].booking.id").value(stay))
                .andExpect(jsonPath("$.lines[0].gross").value("390.00"))
                .andExpect(jsonPath("$.openLines", hasSize(1)))
                .andExpect(jsonPath("$.openLines[0].arrival").value("2027-06-14"))
                .andExpect(jsonPath("$.openLines[0].note").value("Verlängerungsbuchung"));
    }

    @Test
    void withoutOrWithAnUnreadablePdfTheStatementHasNoLines() throws Exception {
        deliver(fewoMail("Juli 2027", null));
        mapFewo();
        assign("Ihre Abrechnung für Juli 2027");
        var without = item("Ihre Abrechnung für Juli 2027");
        assertThat(without.get("result")).isEqualTo("STATEMENT_CREATED");
        assertThat(without.get("resultDetail")).isEqualTo("PDF nicht gelesen");
        getWith("/api/vacationrental/statements/" + statementOf(without), anna.session())
                .andExpect(jsonPath("$.periodFrom").value("2027-07-01"))
                .andExpect(jsonPath("$.statementDate").value("2027-04-04"))
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.invoiceNumber").doesNotExist());

        deliver(fewoMail("August 2027", StatementPdfs.text("Eine ganz andere Übersicht", "Summe 1.000,00")));
        var unreadable = item("Ihre Abrechnung für August 2027");
        assertThat(unreadable.get("result")).isEqualTo("STATEMENT_CREATED");
        assertThat(unreadable.get("resultDetail")).isEqualTo("PDF nicht gelesen");
        var statement = statementOf(unreadable);
        getWith("/api/vacationrental/statements/" + statement, anna.session())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.openLineCount").value(0));
        getWith("/api/documents?ownerType=vacationrental.statement&ownerId=" + statement, anna.session())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void storedStatementMailIsProcessedOnRetry() throws Exception {
        deliver(fewoMail("September 2027", null));
        mapFewo();
        var raw = fewoMail("Oktober 2027", StatementPdfs.pdf(Spec.of(OBJECT, "4713-2027-10", "2027-11-03",
                "Oktober 2027", Row.of("Erika Musterfrau", "2027-10-05", "2027-10-12", "700.00", "90.00",
                        "105.04"))));
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<old-statement@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class,
                anna.id(), raw);
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("STATEMENT_CREATED"))
                .andExpect(jsonPath("$.kind").value("STATEMENT"))
                .andExpect(jsonPath("$.linkType").value("vacationrental.statement"));
    }

    // --- Notices of DS Destination Solutions ---------------------------------------------------

    @Test
    void noticeUsesTheOneUnitOfItsAgency() throws Exception {
        var agency = agency(anna, "Stadtmarketing Seeblick e.V.");
        var name = jdbc.queryForObject("SELECT name FROM vacationrental.agencies WHERE id = ?", String.class, agency);
        mappings.save(new UnitMapping(anna.id(), agency, "Ferienhaus am See", unit));
        deliver(notice("\"" + name + "\" <abrechnung@stadtmarketing.example>", "18.08.2027", "24.08.2027",
                "R-100200300"));

        var created = item("Ihre Abrechnung: R-100200300");
        assertThat(created.get("result")).isEqualTo("STATEMENT_CREATED");
        assertThat(created.get("source")).isEqualTo(name);
        var statement = statementOf(created);
        getWith("/api/vacationrental/statements/" + statement, anna.session())
                .andExpect(jsonPath("$.unit.id").value(unit))
                .andExpect(jsonPath("$.provider").value(name))
                .andExpect(jsonPath("$.invoiceNumber").value("R-100200300"))
                .andExpect(jsonPath("$.periodFrom").value("2027-08-18"))
                .andExpect(jsonPath("$.periodTo").value("2027-08-24"))
                .andExpect(jsonPath("$.lines", hasSize(0)));
        getWith("/api/documents?ownerType=vacationrental.statement&ownerId=" + statement, anna.session())
                .andExpect(jsonPath("$[0].fileName").value("Rechnung R-100200300.pdf"));
    }

    @Test
    void noticeWithoutAClearUnitWaitsAndTheChoiceStays() throws Exception {
        deliver(notice("01.09.2027", "07.09.2027", "R-1"));
        var waiting = item("Ihre Abrechnung: R-1");
        assertThat(waiting.get("result")).isEqualTo("UNASSIGNED_UNIT");
        assertThat(waiting.get("subjectName")).isEqualTo("Abrechnungen");

        assign("Ihre Abrechnung: R-1");
        assertThat(item("Ihre Abrechnung: R-1").get("result")).isEqualTo("STATEMENT_CREATED");

        // A second unit for the agency's booking mails: statements still go to the chosen unit.
        var agency = jdbc.queryForObject("SELECT id FROM vacationrental.agencies WHERE name = ?", Long.class,
                "Stadtmarketing Beispielstadt e.V.");
        var other = unitIds(singleUnitProperty(anna, "Haus Talblick")).getFirst();
        mappings.save(new UnitMapping(anna.id(), agency, "Haus Talblick", other));
        deliver(notice("08.09.2027", "14.09.2027", "R-2"));
        var second = item("Ihre Abrechnung: R-2");
        assertThat(second.get("result")).isEqualTo("STATEMENT_CREATED");
        getWith("/api/vacationrental/statements/" + statementOf(second), anna.session())
                .andExpect(jsonPath("$.unit.id").value(unit));
    }
}
