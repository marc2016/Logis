package app.logis.modules.vacationrental;

import app.logis.core.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money input: exact, at most two decimal places, never negative here. */
final class Money {

    static final BigDecimal MAX = new BigDecimal("999999999.99");

    private Money() {
    }

    static BigDecimal optional(BigDecimal amount) {
        return amount == null ? null : require(amount, "Bitte einen Betrag angeben.");
    }

    static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : require(amount, "Bitte einen Betrag angeben.");
    }

    static BigDecimal require(BigDecimal amount, String missingMessage) {
        if (amount == null) {
            throw ApiException.badRequest(missingMessage);
        }
        if (amount.signum() < 0) {
            throw ApiException.badRequest("Der Betrag darf nicht negativ sein.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Der Betrag darf höchstens zwei Nachkommastellen haben.");
        }
        if (amount.compareTo(MAX) > 0) {
            throw ApiException.badRequest("Der Betrag ist zu groß.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
