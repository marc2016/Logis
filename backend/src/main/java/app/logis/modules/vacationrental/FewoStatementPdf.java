package app.logis.modules.vacationrental;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the statement PDF of Fewo & Meer (spec booking-mails, „Fewo & Meer statement PDF“; design D4 and D5 of
 * statement-mails). Knows exactly one layout: anything unexpected makes the whole PDF unread rather than half read.
 */
final class FewoStatementPdf {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final int MAX_PAGES = 50;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String D = "(\\d{2}\\.\\d{2}\\.\\d{4})";
    private static final String AMOUNT = "[-+]?\\d{1,3}(?:\\.\\d{3})*,\\d{2}";
    private static final Pattern HEADER = Pattern.compile("^Objekt (.+) \\(Re-Nr\\. ([^)]+)\\)$");
    private static final Pattern PERIOD = Pattern.compile(
            "^Zeitraum: (\\p{L}+) (\\d{4}) \\(Rechnungs-Datum: " + D + "\\)$");
    private static final Pattern ROW = Pattern.compile("^(.+?) " + D + " - " + D + "((?: (?:" + AMOUNT + "|"
            + "\\d{2}\\.\\d{2}\\.\\d{4}))+)$");
    private static final Pattern TOTAL = Pattern.compile("^Gesamt((?: " + AMOUNT + ")+)$");
    private static final Pattern BALANCE = Pattern.compile("^Saldo (" + AMOUNT + ")$");
    private static final Pattern AMOUNTS = Pattern.compile(AMOUNT);
    private static final String COMMISSION_TABLE = "Provisionsabrechnung";
    private static final String PAYMENT_TABLE = "Abrechnung Mieten, Rest- und Anzahlungen";
    private static final String EXTENSION = "Verlängerungsbuchung";

    /** One tenant: gross is rent plus additional services, commission includes VAT (design D5). */
    record Row(String tenant, LocalDate arrival, LocalDate departure, BigDecimal gross, BigDecimal commission,
            boolean extension) {
    }

    record Read(String object, String invoiceNumber, LocalDate invoiceDate, YearMonth month, List<Row> rows,
            BigDecimal balance) {
    }

    private FewoStatementPdf() {
    }

    /** The statement, or empty if the PDF is not one of this layout or fails a check. */
    static Optional<Read> read(byte[] pdf) {
        if (pdf == null || pdf.length == 0 || pdf.length > MAX_BYTES) {
            return Optional.empty();
        }
        try (var doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted() || doc.getNumberOfPages() > MAX_PAGES) {
                return Optional.empty();
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return parse(stripper.getText(doc));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** A table's rows with the last amount of each row, and the last amount of its „Gesamt“ row. */
    private record Table(List<Row> rows, BigDecimal total) {
    }

    static Optional<Read> parse(String text) {
        var lines = text.replace("\r", "").replace(' ', ' ').lines().map(String::strip)
                .filter(l -> !l.isEmpty()).toList();
        String object = null;
        String invoice = null;
        LocalDate invoiceDate = null;
        YearMonth month = null;
        Table commission = null;
        Table payment = null;
        BigDecimal balance = null;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            Matcher m;
            if (object == null && (m = HEADER.matcher(line)).matches()) {
                object = m.group(1).strip();
                invoice = m.group(2).strip();
            } else if (month == null && (m = PERIOD.matcher(line)).matches()) {
                var number = GermanText.month(m.group(1));
                if (number.isEmpty()) {
                    return Optional.empty();
                }
                month = YearMonth.of(Integer.parseInt(m.group(2)), number.getAsInt());
                invoiceDate = LocalDate.parse(m.group(3), DATE);
            } else if (commission == null && line.equals(COMMISSION_TABLE)) {
                var table = table(lines, i + 1);
                if (table.isEmpty()) {
                    return Optional.empty();
                }
                commission = table.get();
            } else if (payment == null && line.equals(PAYMENT_TABLE)) {
                var table = table(lines, i + 1);
                if (table.isEmpty()) {
                    return Optional.empty();
                }
                payment = table.get();
            } else if (balance == null && (m = BALANCE.matcher(line)).matches()) {
                balance = amount(m.group(1));
            }
        }
        if (object == null || month == null || commission == null || payment == null || balance == null) {
            return Optional.empty();
        }
        var read = new Read(object, invoice, invoiceDate, month, List.of(), balance);
        return join(commission, payment).map(rows -> new Read(read.object(), read.invoiceNumber(),
                read.invoiceDate(), read.month(), rows, read.balance()));
    }

    /**
     * Rows from {@code start} to the table's „Gesamt“ row. Lines in between that are no row (page header and footer,
     * column headers) are skipped; „Verlängerungsbuchung“ marks the row above. The rows' last amounts must add up to
     * the total's last amount.
     */
    private static Optional<Table> table(List<String> lines, int start) {
        var rows = new ArrayList<Row>();
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            Matcher m;
            if ((m = TOTAL.matcher(line)).matches()) {
                var total = last(m.group(1));
                var sum = rows.stream().map(Row::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
                return rows.isEmpty() || sum.compareTo(total) != 0 ? Optional.empty()
                        : Optional.of(new Table(rows, total));
            }
            if (line.equals(EXTENSION)) {
                if (rows.isEmpty()) {
                    return Optional.empty();
                }
                var previous = rows.removeLast();
                rows.add(new Row(previous.tenant(), previous.arrival(), previous.departure(), previous.gross(),
                        previous.commission(), true));
            } else if ((m = ROW.matcher(line)).matches()) {
                var arrival = LocalDate.parse(m.group(2), DATE);
                var departure = LocalDate.parse(m.group(3), DATE);
                if (departure.isBefore(arrival)) {
                    return Optional.empty();
                }
                // The value of interest is always the row's last column.
                rows.add(new Row(m.group(1).strip(), arrival, departure, last(m.group(4)), null, false));
            } else if (line.equals(COMMISSION_TABLE) || line.equals(PAYMENT_TABLE) || line.startsWith("Saldo ")) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Joins both tables row by row: same tenant and period in the same order, or the PDF is not read. */
    private static Optional<List<Row>> join(Table commission, Table payment) {
        if (commission.rows().size() != payment.rows().size()) {
            return Optional.empty();
        }
        var rows = new ArrayList<Row>();
        for (int i = 0; i < commission.rows().size(); i++) {
            var c = commission.rows().get(i);
            var p = payment.rows().get(i);
            if (!c.tenant().equals(p.tenant()) || !c.arrival().equals(p.arrival())
                    || !c.departure().equals(p.departure()) || c.extension() != p.extension()) {
                return Optional.empty();
            }
            rows.add(new Row(c.tenant(), c.arrival(), c.departure(), p.gross(), c.gross(), c.extension()));
        }
        return Optional.of(rows);
    }

    private static BigDecimal last(String amounts) {
        var m = AMOUNTS.matcher(amounts);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        return amount(last);
    }

    static BigDecimal amount(String value) {
        return new BigDecimal(value.replace("+", "").replace(".", "").replace(',', '.'));
    }
}
