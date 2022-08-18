package net.corda.v5.ledger.consensual;

import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConsensualLedgerServiceJavaApiTest {
    private final ConsensualLedgerService consensualLedgerService = mock(ConsensualLedgerService.class);

    @Test
    public void getTransactionBuilder() {
        final ConsensualTransactionBuilder consensualTransactionBuilder = mock(ConsensualTransactionBuilder.class);
        when(consensualLedgerService.getTransactionBuilder()).thenReturn(consensualTransactionBuilder);

        final ConsensualTransactionBuilder result = consensualLedgerService.getTransactionBuilder();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualTransactionBuilder);
        verify(consensualLedgerService, times(1)).getTransactionBuilder();
    }
}
