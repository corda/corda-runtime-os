package net.corda.v5.ledger.consensual;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
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

        when(consensualLedgerService.findSignedTransaction(any(SecureHash.class))).thenReturn(null);
        when(consensualLedgerService.findLedgerTransaction(any(SecureHash.class))).thenReturn(null);

        final ConsensualTransactionBuilder result = consensualLedgerService.getTransactionBuilder();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualTransactionBuilder);
        verify(consensualLedgerService, times(1)).getTransactionBuilder();

        SecureHash fakeTxId = new SecureHash("SHA256", "1234456".getBytes());
        Assertions.assertThat(consensualLedgerService.findSignedTransaction(fakeTxId)).isNull();
        Assertions.assertThat(consensualLedgerService.findLedgerTransaction(fakeTxId)).isNull();
    }
}
