package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Currency;

public class SourceAndAmountJavaApiTest {

    private final String party = "party";
    private final Currency GBP = Currency.getInstance("GBP");
    private final Amount<Currency> tenPounds = new Amount<>(1000L, GBP);
    private final SourceAndAmount<Currency, String> sourceAndAmount = new SourceAndAmount<>(party, tenPounds, 1);

    @Test
    public void initialize() {
        final SourceAndAmount<Currency, String> sourceAndAmount1 = new SourceAndAmount<>(party, tenPounds);

        Assertions.assertThat(sourceAndAmount).isNotNull();
        Assertions.assertThat(sourceAndAmount1).isNotNull();
    }

    @Test
    public void source() {
        final String source = sourceAndAmount.getSource();

        Assertions.assertThat(source).isEqualTo(party);
    }

    @Test
    public void amount() {
        final Amount<Currency> amount = sourceAndAmount.getAmount();

        Assertions.assertThat(amount).isEqualTo(tenPounds);
    }

    @Test
    public void ref() {
        final Integer ref = (Integer) sourceAndAmount.getRef();

        Assertions.assertThat(ref).isEqualTo(1);
    }
}
