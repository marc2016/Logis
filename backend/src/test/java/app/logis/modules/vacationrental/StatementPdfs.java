package app.logis.modules.vacationrental;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds statement PDFs in the layout of Fewo & Meer from invented rows (design D11 of statement-mails), so no real
 * statement is needed in tests. Header on every page, the commission table, the payment table, the overview with the
 * balance, and the commission table repeated on the last page.
 */
final class StatementPdfs {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final PDRectangle LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

    /** One tenant: rent, additional services and commission (net, VAT 19 % added). */
    record Row(String tenant, LocalDate arrival, LocalDate departure, String rent, String services, String commission,
            boolean extension) {

        static Row of(String tenant, String arrival, String departure, String rent, String services, String commission) {
            return new Row(tenant, LocalDate.parse(arrival), LocalDate.parse(departure), rent, services, commission,
                    false);
        }

        Row asExtension() {
            return new Row(tenant, arrival, departure, rent, services, commission, true);
        }

        BigDecimal commissionVat() {
            return new BigDecimal(commission).multiply(new BigDecimal("0.19")).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal commissionGross() {
            return new BigDecimal(commission).add(commissionVat());
        }

        BigDecimal remaining() {
            return new BigDecimal(rent).add(new BigDecimal(services));
        }
    }

    /** What the document says; {@code rowsPerPage} splits long tables over pages like the agency does. */
    record Spec(String object, String invoiceNumber, LocalDate invoiceDate, String month, List<Row> rows,
            BigDecimal balanceOverride, int rowsPerPage) {

        static Spec of(String object, String invoiceNumber, String invoiceDate, String month, Row... rows) {
            return new Spec(object, invoiceNumber, LocalDate.parse(invoiceDate), month, List.of(rows), null, 20);
        }

        Spec withBalance(String balance) {
            return new Spec(object, invoiceNumber, invoiceDate, month, rows, new BigDecimal(balance), rowsPerPage);
        }

        Spec withRowsPerPage(int n) {
            return new Spec(object, invoiceNumber, invoiceDate, month, rows, balanceOverride, n);
        }

        BigDecimal commissionTotal() {
            return rows.stream().map(Row::commissionGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal remainingTotal() {
            return rows.stream().map(Row::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal balance() {
            return balanceOverride != null ? balanceOverride : remainingTotal().subtract(commissionTotal());
        }
    }

    private StatementPdfs() {
    }

    static String amount(BigDecimal value) {
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return format.format(value);
    }

    private static String amount(String value) {
        return amount(new BigDecimal(value));
    }

    static byte[] pdf(Spec spec) {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var writer = new Writer(doc, spec);
            writer.coverPage();
            writer.tables();
            writer.page();
            writer.line("Auf dieser Seite sind alle Zahlungen für den vergangenen Monat zusammengefasst.");
            writer.page();
            writer.commissionTable(spec.rows(), Integer.MAX_VALUE);
            writer.line("Zusatzrechnungen");
            writer.cells(60, "Rechnung für", "Netto", "MwSt.", "Brutto");
            writer.cells(60, "Gesamt", "0,00", "0,00", "0,00");
            writer.finish();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A PDF with any text, e.g. another agency's document. */
    static byte[] text(String... lines) {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                float y = 780;
                for (var line : lines) {
                    content.beginText();
                    content.newLineAtOffset(50, y);
                    content.showText(line);
                    content.endText();
                    y -= 14;
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class Writer {

        private final PDDocument doc;
        private final Spec spec;
        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        Writer(PDDocument doc, Spec spec) {
            this.doc = doc;
            this.spec = spec;
        }

        void page() throws IOException {
            if (content != null) {
                footer();
                content.close();
            }
            var page = new PDPage(LANDSCAPE);
            doc.addPage(page);
            content = new PDPageContentStream(doc, page);
            pageNumber++;
            y = LANDSCAPE.getHeight() - 40;
            line("Objekt " + spec.object() + " (Re-Nr. " + spec.invoiceNumber() + ")");
            line("Zeitraum: " + spec.month() + " (Rechnungs-Datum: " + DATE.format(spec.invoiceDate()) + ")");
            y -= 10;
        }

        private void footer() throws IOException {
            text(40, 30, "Dokument maschinell erstellt am " + DATE.format(spec.invoiceDate()));
            text(720, 30, "Seite " + pageNumber);
        }

        void finish() throws IOException {
            footer();
            content.close();
        }

        void coverPage() throws IOException {
            page();
            cells(500, "Beispiel GbR", "Fewo & Meer e.K.");
            cells(500, "Musterweg 1", "Ferienhausvermittlung");
            cells(500, "12345 Musterstadt", "Beispielweg 2");
            y -= 10;
            line("Sehr geehrte(r) Beispiel GbR,");
            line("anbei erhalten Sie die Abrechnung " + spec.month() + " für Ihr Objekt:");
            line(spec.object());
            line("Mit freundlichen Grüßen");
        }

        void tables() throws IOException {
            page();
            commissionTable(spec.rows(), spec.rowsPerPage());
            line("Abrechnung Mieten, Rest- und Anzahlungen");
            cells(140, "Mieter", "Mietzeitraum", "Buchungs-", "Miete", "Anzahlung", "Kaution", "Strom", "Strom",
                    "Wasser", "Gas", "Sonstiges", "Zusätz.", "Kautions-", "Miete/Zusätzl.");
            cells(140, "", "", "datum", "", "", "", "", "(NT)", "", "", "", "Leistungen", "rückzahlung", "Leistungen");
            line("(Restzahlungen)");
            int n = 0;
            for (var row : spec.rows()) {
                if (n > 0 && n % spec.rowsPerPage() == 0) {
                    page();
                    cells(140, "Mieter", "Mietzeitraum", "Buchungs-", "Miete", "Anzahlung");
                }
                cells(140, row.tenant(), period(row), DATE.format(row.arrival().minusMonths(2)), amount(row.rent()),
                        "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", amount(row.services()), "0,00",
                        amount(row.remaining()));
                extensionMark(row);
                n++;
            }
            var rent = spec.rows().stream().map(r -> new BigDecimal(r.rent())).reduce(BigDecimal.ZERO, BigDecimal::add);
            var services = spec.rows().stream().map(r -> new BigDecimal(r.services()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            cells(140, "Gesamt", "", "", amount(rent), "0,00", "0,00", "0,00", amount(services),
                    amount(spec.remainingTotal()));
            y -= 10;
            line("Alle Zahlungen zur Übersicht");
            cells(400, "Provision Brutto", "-" + amount(spec.commissionTotal()));
            cells(400, "Restzahlungen", "+" + amount(spec.remainingTotal()));
            cells(400, "Verbrauch (Abzug von Kaution)", "0,00");
            cells(400, "Saldo", amount(spec.balance()));
        }

        void commissionTable(List<Row> rows, int perPage) throws IOException {
            line("Provisionsabrechnung");
            var header = new String[] {"Mieter", "Mietzeitraum", "Miete", "Zusätz. Leistungen", "Provision",
                    "Zusätzliche Gebühr", "Gesamt Netto", "MwSt. (19.0%)", "Gesamt Brutto"};
            cells(140, header);
            int n = 0;
            for (var row : rows) {
                if (n > 0 && n % perPage == 0) {
                    page();
                    cells(140, header);
                }
                cells(140, row.tenant(), period(row), amount(row.rent()), amount(row.services()),
                        amount(row.commission()), "0,00", amount(row.commission()), amount(row.commissionVat()),
                        amount(row.commissionGross()));
                extensionMark(row);
                n++;
            }
            var rent = rows.stream().map(r -> new BigDecimal(r.rent())).reduce(BigDecimal.ZERO, BigDecimal::add);
            var net = rows.stream().map(r -> new BigDecimal(r.commission())).reduce(BigDecimal.ZERO, BigDecimal::add);
            var vat = rows.stream().map(Row::commissionVat).reduce(BigDecimal.ZERO, BigDecimal::add);
            cells(140, "Gesamt", "", amount(rent), "0,00", amount(net), "0,00", amount(net), amount(vat),
                    amount(net.add(vat)));
            y -= 10;
        }

        private void extensionMark(Row row) throws IOException {
            if (row.extension()) {
                text(180, y, "Verlängerungsbuchung");
                y -= 14;
            }
        }

        private static String period(Row row) {
            return DATE.format(row.arrival()) + " - " + DATE.format(row.departure());
        }

        void line(String text) throws IOException {
            text(40, y, text);
            y -= 14;
        }

        /** The first cell at x=40, the second at x=40+first, the others every 48 points. */
        void cells(float first, String... values) throws IOException {
            float x = 40;
            for (int i = 0; i < values.length; i++) {
                if (!values[i].isEmpty()) {
                    text(x, y, values[i]);
                }
                x += i == 0 ? first : i == 1 ? 110 : 42;
            }
            y -= 14;
        }

        private void text(float x, float yPos, String value) throws IOException {
            content.beginText();
            content.setFont(font, 7);
            content.newLineAtOffset(x, yPos);
            content.showText(value);
            content.endText();
        }
    }
}
