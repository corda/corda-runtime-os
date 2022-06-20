package net.corda.v5.ledger.obsolete.crypto;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionDigestAlgorithmNamesJavaApiTest {

    private final TransactionDigestAlgorithmNamesFactory factory = mock(TransactionDigestAlgorithmNamesFactory.class);
    private final TransactionDigestAlgorithmNames transactionDigestAlgorithmNames = new TransactionDigestAlgorithmNames();

    @Test
    public void create() {
        when(factory.create()).thenReturn(transactionDigestAlgorithmNames);

        TransactionDigestAlgorithmNames result = factory.create();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionDigestAlgorithmNames);
    }
}
