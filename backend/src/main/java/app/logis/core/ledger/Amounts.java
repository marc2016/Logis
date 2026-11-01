package app.logis.core.ledger;

import app.logis.core.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validation of money amounts: exact, at most two decimal places, never rounded.
 */
final class Amounts {

    static final BigDecimal MAX = new BigDecimal("999999999.99");

    private Amounts() {
    }

    /** For transaction amounts: non-zero. */
    static BigDecimal requireTransactionAmount(BigDecimal amount) {
        var value = requireMoney(amount, "Bitte einen Betrag angeben.");
        if (value.signum() == 0) {
            throw ApiException.badRequest("Der Betrag darf nicht 0 sein.");
        }
        return value;
    }

    /** For balances such as the opening balance: 0 is allowed too. */
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
