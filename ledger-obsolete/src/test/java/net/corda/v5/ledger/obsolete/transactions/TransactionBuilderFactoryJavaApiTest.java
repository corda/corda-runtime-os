package net.corda.v5.ledger.obsolete.transactions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionBuilderFactoryJavaApiTest {

    private final TransactionBuilderFactory transactionBuilderFactory = mock(TransactionBuilderFactory.class);
    private final TransactionBuilder transactionBuilder = mock(TransactionBuilder.class);

    @Test
    public void create() {
        when(transactionBuilderFactory.create()).thenReturn(transactionBuilder);

        TransactionBuilder result = transactionBuilderFactory.create();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionBuilder);
    }
}
