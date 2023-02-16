package net.corda.v5.ledger.consensual;

import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction;
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction;
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator;
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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

        SecureHash fakeTransactionId = new SecureHash("SHA256", "1234456".getBytes());
        Assertions.assertThat(consensualLedgerService.findSignedTransaction(fakeTransactionId)).isNull();
        Assertions.assertThat(consensualLedgerService.findLedgerTransaction(fakeTransactionId)).isNull();
    }

    @Test
    public void findSignedTransaction() {
        SecureHash secureHash = new SecureHash("SHA-256", "123".getBytes());
        ConsensualSignedTransaction consensualSignedTransaction = mock(ConsensualSignedTransaction.class);
        when(consensualLedgerService.findSignedTransaction(secureHash)).thenReturn(consensualSignedTransaction);

        ConsensualSignedTransaction result = consensualLedgerService.findSignedTransaction(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualSignedTransaction);
        verify(consensualLedgerService, times(1)).findSignedTransaction(secureHash);
    }

    @Test
    public void findLedgerTransaction() {
        SecureHash secureHash = new SecureHash("SHA-256", "123".getBytes());
        ConsensualLedgerTransaction consensualLedgerTransaction = mock(ConsensualLedgerTransaction.class);
        when(consensualLedgerService.findLedgerTransaction(secureHash)).thenReturn(consensualLedgerTransaction);

        ConsensualLedgerTransaction result = consensualLedgerService.findLedgerTransaction(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualLedgerTransaction);
        verify(consensualLedgerService, times(1)).findLedgerTransaction(secureHash);
    }

    @Test
    public void finalizeTest() {
        ConsensualSignedTransaction consensualSignedTransactionIn = mock(ConsensualSignedTransaction.class);
        ConsensualSignedTransaction consensualSignedTransactionOut = mock(ConsensualSignedTransaction.class);
        List<FlowSession> flowSessions = Arrays.asList(mock(FlowSession.class), mock(FlowSession.class));
        when(consensualLedgerService.finalize(consensualSignedTransactionIn, flowSessions)).thenReturn(consensualSignedTransactionOut);

        ConsensualSignedTransaction result = consensualLedgerService.finalize(consensualSignedTransactionIn, flowSessions);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualSignedTransactionOut);
        verify(consensualLedgerService, times(1)).finalize(consensualSignedTransactionIn, flowSessions);
    }

    @Test
    public void receiveFinality() {
        ConsensualSignedTransaction consensualSignedTransaction = mock(ConsensualSignedTransaction.class);
        FlowSession flowSession = mock(FlowSession.class);
        ConsensualTransactionValidator consensualTransactionValidator = mock(ConsensualTransactionValidator.class);
        when(consensualLedgerService.receiveFinality(flowSession, consensualTransactionValidator)).thenReturn(consensualSignedTransaction);

        ConsensualSignedTransaction result = consensualLedgerService.receiveFinality(flowSession, consensualTransactionValidator);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(consensualSignedTransaction);
        verify(consensualLedgerService, times(1)).receiveFinality(flowSession, consensualTransactionValidator);
    }
}
