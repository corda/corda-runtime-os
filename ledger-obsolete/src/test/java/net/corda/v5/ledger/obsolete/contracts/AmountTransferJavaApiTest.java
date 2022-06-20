package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

public class AmountTransferJavaApiTest {

    private final String partyA = "partyA";
    private final String partyB = "partyB";
    private final Currency GBP = Currency.getInstance("GBP");
    private final Amount<Currency> onePounds = new Amount<>(200L, GBP);
    private final SourceAndAmount<Currency, String> sourceAndAmount = new SourceAndAmount<>(partyA, onePounds, 1);
    private final AmountTransfer<Currency, String> amountTransfer = new AmountTransfer<>(200L, GBP, partyA, partyB);

    @Test
    public void quantityDelta() {
        final Long quantityDelta = amountTransfer.getQuantityDelta();

        Assertions.assertThat(quantityDelta).isNotNull();
        Assertions.assertThat(quantityDelta).isEqualTo(200);
    }

    @Test
    public void token() {
        final Currency token = amountTransfer.getToken();

        Assertions.assertThat(token).isNotNull();
        Assertions.assertThat(token).isEqualTo(GBP);
    }

    @Test
    public void source() {
        final String party = amountTransfer.getSource();

        Assertions.assertThat(party).isNotNull();
        Assertions.assertThat(party).isEqualTo(partyA);
    }

    @Test
    public void destination() {
        final String party = amountTransfer.getDestination();

        Assertions.assertThat(party).isNotNull();
        Assertions.assertThat(party).isEqualTo(partyB);
    }

    @Test
    public void fromDecimal() {
        AmountTransfer<Currency, String> amountTransfer1 =
                AmountTransfer.fromDecimal(new BigDecimal("2"), GBP, partyA, partyB);

        Assertions.assertThat(amountTransfer1).isEqualTo(amountTransfer);
    }

    @Test
    public void fromDecimal_withRounding() {
        AmountTransfer<Currency, String> amountTransfer1 =
                AmountTransfer.fromDecimal(new BigDecimal("2"), GBP, partyA, partyB, RoundingMode.FLOOR);

        Assertions.assertThat(amountTransfer1).isEqualTo(amountTransfer);
    }

    @Test
    public void zero() {
        final AmountTransfer<Currency, String> amountTransfer1 = AmountTransfer.zero(GBP, partyA, partyB);
        final AmountTransfer<Currency, String> amountTransfer2 = new AmountTransfer<>( 0L, GBP, partyA, partyB);

        Assertions.assertThat(amountTransfer1).isEqualTo(amountTransfer2);
    }

    @Test
    public void plus() {
        final AmountTransfer<Currency, String> amountTransfer1 = amountTransfer.plus(amountTransfer);

        Assertions.assertThat(amountTransfer1.getQuantityDelta()).isEqualTo(400);
    }

    @Test
    public void toDecimal() {
        final BigDecimal decimal = amountTransfer.toDecimal();

        Assertions.assertThat(decimal).isEqualTo("2.00");
    }

    @Test
    public void copy_withoutArguments() {
        AmountTransfer<Currency, String> copy = amountTransfer.copy();

        Assertions.assertThat(copy).isEqualTo(amountTransfer);
    }

    @Test
    public void copy_withQuantityDelta() {
        AmountTransfer<Currency, String> copy = amountTransfer.copy(2);

        Assertions.assertThat(copy.getQuantityDelta()).isEqualTo(2);
    }

    @Test
    public void copy_withToken() {
        final Currency token = Currency.getInstance("GBP");
        AmountTransfer<Currency, String> copy = amountTransfer.copy(amountTransfer.getQuantityDelta(), token);

        Assertions.assertThat(copy.getToken()).isEqualTo(token);
    }

    @Test
    public void copy_withSource() {
        final String source = "new-source";
        AmountTransfer<Currency, String> copy = amountTransfer.copy(
                amountTransfer.getQuantityDelta(), amountTransfer.getToken(), source
        );

        Assertions.assertThat(copy.getSource()).isEqualTo(source);
    }

    @Test
    public void copy_withDestination() {
        final String destination = "new-destination";
        AmountTransfer<Currency, String> copy = amountTransfer.copy(
                amountTransfer.getQuantityDelta(), amountTransfer.getToken(), amountTransfer.getSource(), destination
        );

        Assertions.assertThat(copy.getDestination()).isEqualTo(destination);
    }

    @Test
    public void equals() {
        final AmountTransfer<Currency, String> amountTransfer1 = new AmountTransfer<>(200L, GBP, partyA, partyB);
        final AmountTransfer<Currency, String> amountTransfer2 = new AmountTransfer<>(201L, GBP, partyA, partyB);

        final boolean equaled = amountTransfer.equals(amountTransfer1);
        final boolean notEqualed = amountTransfer.equals(amountTransfer2);

        Assertions.assertThat(equaled).isTrue();
        Assertions.assertThat(notEqualed).isFalse();
    }

    @Test
    public void _hashCode() {
        final Integer integer = amountTransfer.hashCode();

        Assertions.assertThat(integer).isNotNull();
    }

    @Test
    public void _toString() {
        final String toString = "Transfer from " + amountTransfer.getSource() + " to " + amountTransfer.getDestination()
                + " of " + amountTransfer.toDecimal().toPlainString() + " " + amountTransfer.getToken();

        Assertions.assertThat(amountTransfer.toString()).isEqualTo(toString);
    }

    @Test
    public void novate() {
        final String centralParty = "centralParty";
        final List<AmountTransfer<Currency, String>> amountTransferList = amountTransfer.novate(centralParty);

        Assertions.assertThat(amountTransferList.size()).isEqualTo(2);
    }

    @Test
    public void apply() {
        final List<SourceAndAmount<Currency, String>> sourceAndAmountList = List.of(sourceAndAmount);
        final List<SourceAndAmount<Currency, String>> sourceAndAmountList1 = amountTransfer.apply(sourceAndAmountList);

        Assertions.assertThat(sourceAndAmountList).isNotEqualTo(sourceAndAmountList1);
    }

    @Test
    public void apply_withNewRef() {
        final List<SourceAndAmount<Currency, String>> sourceAndAmountList = List.of(sourceAndAmount);
        final List<SourceAndAmount<Currency, String>> sourceAndAmountList1 = amountTransfer.apply(sourceAndAmountList, sourceAndAmount.getRef());

        Assertions.assertThat(sourceAndAmountList).isNotEqualTo(sourceAndAmountList1);
    }
}
