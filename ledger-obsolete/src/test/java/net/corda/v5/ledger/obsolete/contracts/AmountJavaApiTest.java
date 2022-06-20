package net.corda.v5.ledger.obsolete.contracts;

import net.corda.v5.ledger.obsolete.contracts.TokenizableAssetInfoJavaApiTest.TestAsset;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;

public class AmountJavaApiTest {

    private final BigDecimal displayQuantity = new BigDecimal("100");
    private final Currency GBP = Currency.getInstance("GBP");
    private final Amount<Currency> tenPounds = new Amount<>(1000L, GBP);

    @Test
    public void quantity() {
        final Long aLong = tenPounds.getQuantity();

        Assertions.assertThat(aLong).isNotNull();
        Assertions.assertThat(aLong).isEqualTo(1000L);
    }

    @Test
    public void displayTokenSize() {
        final BigDecimal bigDecimal = tenPounds.getDisplayTokenSize();

        Assertions.assertThat(bigDecimal).isNotNull();
    }

    @Test
    public void token() {
        final Currency currency = tenPounds.getToken();

        Assertions.assertThat(currency).isNotNull();
    }

    @Test
    public void fromDecimal_withAllArguments() {
        final Integer integer = 5;
        final Amount<Integer> integerAmount = Amount.fromDecimal(displayQuantity, integer, RoundingMode.FLOOR);

        Assertions.assertThat(integerAmount).isNotNull();
        Assertions.assertThat(integerAmount.getToken()).isEqualTo(integer);
        Assertions.assertThat(integerAmount.getQuantity()).isEqualTo(displayQuantity.longValue());
    }

    @Test
    public void fromDecimal_withoutRounding() {
        final Integer integer = 5;
        final Amount<Integer> integerAmount = Amount.fromDecimal(displayQuantity, integer);

        Assertions.assertThat(integerAmount).isNotNull();
        Assertions.assertThat(integerAmount.getToken()).isEqualTo(integer);
        Assertions.assertThat(integerAmount.getQuantity()).isEqualTo(displayQuantity.longValue());
    }

    @Test
    public void zero() {
        final Integer integer = 5;
        final Amount<Integer> integerAmount = Amount.zero(integer);

        Assertions.assertThat(integerAmount).isNotNull();
        Assertions.assertThat(integerAmount.getToken()).isEqualTo(integer);
        Assertions.assertThat(integerAmount.getQuantity()).isEqualTo(0);
    }

    @Test
    public void displayTokenSize_ForCurrency() {
        final BigDecimal bigDecimal = Amount.getDisplayTokenSize(tenPounds.getToken());

        Assertions.assertThat(bigDecimal).isNotNull();
        Assertions.assertThat(bigDecimal).isEqualTo("0.01");
    }

    @Test
    public void displayTokenSize_ForTokenizableAssetInfo() {
        final TestAsset asset = new TestAsset();
        final BigDecimal bigDecimal = Amount.getDisplayTokenSize(asset);

        Assertions.assertThat(bigDecimal).isNotNull();
        Assertions.assertThat(bigDecimal).isEqualTo("100");
    }

    @Test
    public void sumOrNull() {
        List<Amount<Currency>> amountList = List.of(tenPounds, tenPounds);

        Amount<Currency> sum = Amount.sumOrNull(amountList);

        assert sum != null;
        Assertions.assertThat(sum.getQuantity()).isEqualTo(2000L);
    }

    @Test
    public void sumOrThrow() {
        List<Amount<Currency>> amountList = List.of(tenPounds, tenPounds);

        Amount<Currency> sum = Amount.sumOrThrow(amountList);

        Assertions.assertThat(sum.getQuantity()).isEqualTo(2000L);
    }

    @Test
    public void sumOrZero() {
        List<Amount<Currency>> amountList = List.of(tenPounds, tenPounds);

        Amount<Currency> sum = Amount.sumOrZero(amountList, tenPounds.getToken());

        Assertions.assertThat(sum.getQuantity()).isEqualTo(2000L);
    }

    @Test
    public void parseCurrency() {
        final Amount<Currency> pounds1 = Amount.parseCurrency("10 GBP");
        final Amount<Currency> pounds2 = Amount.parseCurrency("£10");

        Assertions.assertThat(tenPounds).isEqualTo(pounds1);
        Assertions.assertThat(tenPounds).isEqualTo(pounds2);
    }

    @Test
    public void plus() {
        final Amount<Currency> pounds1 = new Amount<>(100L, GBP);

        final Amount<Currency> pounds2 = tenPounds.plus(pounds1);
        final Amount<Currency> pounds3 = Amount.parseCurrency("£11");

        Assertions.assertThat(pounds2).isEqualTo(pounds3);
    }

    @Test
    public void minus() {
        final Amount<Currency> pounds1 = new Amount<>(100L, GBP);

        final Amount<Currency> pounds2 = tenPounds.minus(pounds1);
        final Amount<Currency> pounds3 = Amount.parseCurrency("£9");

        Assertions.assertThat(pounds2).isEqualTo(pounds3);
    }

    @Test
    public void time_withLong() {
        final Amount<Currency> pounds1 = tenPounds.times(2L);
        final Amount<Currency> pounds2 = Amount.parseCurrency("£20");

        Assertions.assertThat(pounds1).isEqualTo(pounds2);
    }

    @Test
    public void time_withInt() {
        final Amount<Currency> pounds1 = tenPounds.times(2);
        final Amount<Currency> pounds2 = Amount.parseCurrency("£20");

        Assertions.assertThat(pounds1).isEqualTo(pounds2);
    }

    @Test
    public void splitEvenly() {
        final List<Amount<Currency>> amountList = tenPounds.splitEvenly(2);
        final Amount<Currency> pounds2 = Amount.parseCurrency("£5");

        Assertions.assertThat(amountList.size()).isEqualTo(2);
        Assertions.assertThat(amountList.get(0)).isEqualTo(pounds2);
    }

    @Test
    public void toDecimal() {
        final BigDecimal decimalAmount = tenPounds.toDecimal();

        Assertions.assertThat(decimalAmount).isEqualTo("10.00");
    }

    @Test
    public void _toString() {
        final String stringAmount = tenPounds.toString();

        Assertions.assertThat(stringAmount).isEqualTo("10.00 GBP");
    }

    @Test
    public void compareTo() {
        final Amount<Currency> pounds1 = Amount.parseCurrency("10 GBP");

        final int i = tenPounds.compareTo(pounds1);

        Assertions.assertThat(i).isEqualTo(0);
    }
}
