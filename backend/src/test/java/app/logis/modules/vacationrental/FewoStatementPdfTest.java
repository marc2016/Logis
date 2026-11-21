package app.logis.modules.vacationrental;

import app.logis.modules.vacationrental.StatementPdfs.Row;
import app.logis.modules.vacationrental.StatementPdfs.Spec;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** The Fewo & Meer statement PDF (spec booking-mails, „Fewo & Meer statement PDF“). All values are invented. */
class FewoStatementPdfTest {

    private static final Row ERIKA = Row.of("Erika Musterfrau", "2027-03-05", "2027-03-12", "700.00", "90.00",
            "105.04");
    private static final Row MAX_EXT = Row.of("Max von Beispiel-Muster", "2027-03-14", "2027-03-15", "80.00", "0.00",
            "12.00").asExtension();
    private static final Row MAX = Row.of("Max von Beispiel-Muster", "2027-03-15", "2027-03-18", "300.00", "90.00",
            "45.00");
    private static final Row OTTO = Row.of("Otto", "2027-02-26", "2027-03-03", "1250.50", "90.00", "187.58");

    private static Spec march(Row... rows) {
        return Spec.of("Haus Düne - Beispielort", "9999-2027-03", "2027-04-03", "März 2027", rows);
    }

    private static String text(byte[] pdf) throws Exception {
        try (var doc = Loader.loadPDF(pdf)) {
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    @Test
    void helperBuildsTheThreeParts() throws Exception {
        var text = text(StatementPdfs.pdf(march(ERIKA, MAX_EXT, MAX)));
        assertThat(text).contains("Provisionsabrechnung", "Abrechnung Mieten, Rest- und Anzahlungen", "Saldo ",
                "Verlängerungsbuchung", "Re-Nr. 9999-2027-03");
    }

    @Test
    void readsHeaderRowsAndBalance() {
        var read = FewoStatementPdf.read(StatementPdfs.pdf(march(ERIKA, MAX_EXT, MAX, OTTO))).orElseThrow();
        assertThat(read.object()).isEqualTo("Haus Düne - Beispielort");
        assertThat(read.invoiceNumber()).isEqualTo("9999-2027-03");
        assertThat(read.invoiceDate()).isEqualTo(LocalDate.of(2027, 4, 3));
        assertThat(read.month()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(read.rows()).hasSize(4);

        var erika = read.rows().getFirst();
        assertThat(erika.tenant()).isEqualTo("Erika Musterfrau");
        assertThat(erika.arrival()).isEqualTo(LocalDate.of(2027, 3, 5));
        assertThat(erika.departure()).isEqualTo(LocalDate.of(2027, 3, 12));
        // Gross is rent plus additional services, commission includes 19 % VAT.
        assertThat(erika.gross()).isEqualByComparingTo("790.00");
        assertThat(erika.commission()).isEqualByComparingTo("125.00");
        assertThat(erika.extension()).isFalse();

        // A name with several words and a hyphen; the extension mark belongs to the row above it.
        assertThat(read.rows().get(1).tenant()).isEqualTo("Max von Beispiel-Muster");
        assertThat(read.rows().get(1).extension()).isTrue();
        assertThat(read.rows().get(2).extension()).isFalse();
        // Thousands separator.
        assertThat(read.rows().get(3).gross()).isEqualByComparingTo("1340.50");

        var balance = read.rows().stream().map(r -> r.gross().subtract(r.commission()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(read.balance()).isEqualByComparingTo(balance);
    }

    @Test
    void tablesOverSeveralPages() {
        var rows = new ArrayList<Row>();
        for (int i = 1; i <= 25; i++) {
            rows.add(Row.of("Gast Nummer" + i, "2027-03-01", "2027-03-02", "100.00", "10.00", "15.00"));
        }
        var spec = new Spec("Haus Düne", "1", LocalDate.of(2027, 4, 3), "März 2027", rows, null, 8);
        var read = FewoStatementPdf.read(StatementPdfs.pdf(spec)).orElseThrow();
        assertThat(read.rows()).hasSize(25);
        assertThat(read.rows().getLast().tenant()).isEqualTo("Gast Nummer25");
    }

    @Test
    void statedBalanceIsKeptAsItIs() {
        var read = FewoStatementPdf.read(StatementPdfs.pdf(march(ERIKA).withBalance("600.00"))).orElseThrow();
        assertThat(read.balance()).isEqualByComparingTo("600.00");
    }

    @Test
    void totalThatDoesNotAddUpIsNotRead() {
        var text = FewoStatementPdf.parse(sampleText().replace("90,00 790,00\n", "90,00 791,00\n"));
        assertThat(text).isEmpty();
        assertThat(FewoStatementPdf.parse(sampleText())).isPresent();
    }

    @Test
    void missingPartIsNotRead() {
        assertThat(FewoStatementPdf.parse(sampleText().replace("Saldo 665,00", ""))).isEmpty();
        assertThat(FewoStatementPdf.parse(sampleText().replace("Abrechnung Mieten, Rest- und Anzahlungen", "")))
                .isEmpty();
    }

    @Test
    void otherDocumentsAreNotRead() {
        assertThat(FewoStatementPdf.read(StatementPdfs.text("Eigentümerabrechnung", "Zeitraum 01.03.2027 bis "
                + "31.03.2027", "Summe 1.000,00"))).isEmpty();
        assertThat(FewoStatementPdf.read("%PDF-1.4 kaputt".getBytes())).isEmpty();
        assertThat(FewoStatementPdf.read(new byte[0])).isEmpty();
    }

    @Test
    void pageLimit() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i <= FewoStatementPdf.MAX_PAGES; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            assertThat(FewoStatementPdf.read(out.toByteArray())).isEmpty();
        }
    }

    @Test
    void endToEndFixtureIsRead() throws Exception {
        // e2e/fixtures/statement-fewo.pdf was built with StatementPdfs from invented rows; e2e expects these values.
        var pdf = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../e2e/fixtures/statement-fewo.pdf"));
        var read = FewoStatementPdf.read(pdf).orElseThrow();
        assertThat(read.object()).isEqualTo("Haus Düne - Beispielort");
        assertThat(read.invoiceNumber()).isEqualTo("E2E-2027-03");
        assertThat(read.rows()).extracting(FewoStatementPdf.Row::tenant).containsExactly("Erika Musterfrau",
                "Max Mustermann");
        assertThat(read.balance()).isEqualByComparingTo("927.51");
    }

    /** The text PDFBox gives for a one-row statement, to change single lines. */
    private static String sampleText() {
        return """
                Objekt Haus Düne (Re-Nr. 1-2027-03)
                Zeitraum: März 2027 (Rechnungs-Datum: 03.04.2027)
                Provisionsabrechnung
                Mieter Mietzeitraum Miete Zusätz. Leistungen Provision
                Erika Musterfrau 05.03.2027 - 12.03.2027 700,00 90,00 105,04 0,00 105,04 19,96 125,00
                Gesamt 700,00 90,00 105,04 0,00 105,04 19,96 125,00
                Abrechnung Mieten, Rest- und Anzahlungen
                Erika Musterfrau 05.03.2027 - 12.03.2027 05.01.2027 700,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 90,00 0,00 790,00
                Gesamt 700,00 0,00 0,00 90,00 790,00
                Alle Zahlungen zur Übersicht
                Provision Brutto -125,00
                Saldo 665,00
                """;
    }
}
