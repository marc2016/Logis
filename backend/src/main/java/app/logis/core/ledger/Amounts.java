package app.logis.core.ledger;

import app.logis.core.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pruefung von Geldbetraegen: exakt, hoechstens zwei Nachkommastellen, nie gerundet.
 */
final class Amounts {

    static final BigDecimal MAX = new BigDecimal("999999999.99");

    private Amounts() {
    }

    /** Fuer Transaktionsbetraege: ungleich 0. */
    static BigDecimal requireTransactionAmount(BigDecimal amount) {
        var value = requireMoney(amount, "Bitte einen Betrag angeben.");
        if (value.signum() == 0) {
            throw ApiException.badRequest("Der Betrag darf nicht 0 sein.");
        }
        return value;
    }

    /** Fuer Salden wie den Anfangssaldo: auch 0 erlaubt. */
    static BigDecimal requireMoney(BigDecimal amount, String missingMessage) {
        if (amount == null) {
            throw ApiException.badRequest(missingMessage);
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Der Betrag darf höchstens zwei Nachkommastellen haben.");
        }
        if (amount.abs().compareTo(MAX) > 0) {
            throw ApiException.badRequest("Der Betrag ist zu groß.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
