package app.logis.core.ledger;

import app.logis.core.web.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountsAndIbanTest {

    @Test
    void validIbans() {
        assertThat(Iban.normalize("DE89 3704 0044 0532 0130 00")).isEqualTo("DE89370400440532013000");
        assertThat(Iban.normalize("at611904300234573201")).isEqualTo("AT611904300234573201");
        assertThat(Iban.normalize("  ")).isNull();
        assertThat(Iban.normalize(null)).isNull();
    }

    @Test
    void invalidIbans() {
        for (var iban : new String[] {"DE88370400440532013000", "DE8937040044", "1234567890123456", "DE89-3704"}) {
            assertThatThrownBy(() -> Iban.normalize(iban)).isInstanceOf(ApiException.class)
                    .hasMessage("Die IBAN ist ungültig");
        }
    }

    @Test
    void amounts() {
        assertThat(Amounts.requireTransactionAmount(new BigDecimal("-84.9"))).isEqualByComparingTo("-84.90")
                .hasToString("-84.90");
        assertThat(Amounts.requireTransactionAmount(new BigDecimal("12.340"))).hasToString("12.34");
        assertThat(Amounts.requireTransactionAmount(new BigDecimal("999999999.99"))).hasToString("999999999.99");
        assertThat(Amounts.requireMoney(BigDecimal.ZERO, "x")).hasToString("0.00");

        assertThatThrownBy(() -> Amounts.requireTransactionAmount(new BigDecimal("0.00")))
                .hasMessage("Der Betrag darf nicht 0 sein.");
        assertThatThrownBy(() -> Amounts.requireTransactionAmount(new BigDecimal("12.345")))
                .hasMessageContaining("zwei Nachkommastellen");
        assertThatThrownBy(() -> Amounts.requireTransactionAmount(new BigDecimal("1000000000.00")))
                .hasMessage("Der Betrag ist zu groß.");
        assertThatThrownBy(() -> Amounts.requireTransactionAmount(null)).isInstanceOf(ApiException.class);
    }
}
