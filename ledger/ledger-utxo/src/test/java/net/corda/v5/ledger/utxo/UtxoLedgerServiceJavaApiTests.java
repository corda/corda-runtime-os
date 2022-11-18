package net.corda.v5.ledger.utxo;

import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class UtxoLedgerServiceJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getTransactionBuilderShouldReturnTheExpectedResult() {
        UtxoTransactionBuilder value = utxoLedgerService.getTransactionBuilder();
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void resolvePluralShouldReturnTheExpectedResult() {
        List<StateAndRef<ContractState>> value = utxoLedgerService.resolve(List.of(stateRef));
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void resolveSingularShouldReturnTheExpectedResult() {
        StateAndRef<ContractState> value = utxoLedgerService.resolve(stateRef);
        Assertions.assertEquals(contractStateAndRef, value);
    }

    @Test
    public void findSignedTransaction() {
        SecureHash secureHash = new SecureHash("SHA-256", "123".getBytes());
        UtxoSignedTransaction utxoSignedTransaction = mock(UtxoSignedTransaction.class);
        when(utxoLedgerService.findSignedTransaction(secureHash)).thenReturn(utxoSignedTransaction);

        UtxoSignedTransaction result = utxoLedgerService.findSignedTransaction(secureHash);

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(utxoSignedTransaction);
        verify(utxoLedgerService, times(1)).findSignedTransaction(secureHash);
    }

    @Test
    public void findLedgerTransaction() {
        SecureHash secureHash = new SecureHash("SHA-256", "123".getBytes());
        UtxoLedgerTransaction utxoLedgerTransaction = mock(UtxoLedgerTransaction.class);
        when(utxoLedgerService.findLedgerTransaction(secureHash)).thenReturn(utxoLedgerTransaction);

        UtxoLedgerTransaction result = utxoLedgerService.findLedgerTransaction(secureHash);

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(utxoLedgerTransaction);
        verify(utxoLedgerService, times(1)).findLedgerTransaction(secureHash);
    }

    @Test
    public void finalizeTest() {
        UtxoSignedTransaction UtxoSignedTransactionIn = mock(UtxoSignedTransaction.class);
        UtxoSignedTransaction UtxoSignedTransactionOut = mock(UtxoSignedTransaction.class);
        List<FlowSession> flowSessions = Arrays.asList(mock(FlowSession.class), mock(FlowSession.class));
        when(utxoLedgerService.finalize(UtxoSignedTransactionIn, flowSessions)).thenReturn(UtxoSignedTransactionOut);

        UtxoSignedTransaction result = utxoLedgerService.finalize(UtxoSignedTransactionIn, flowSessions);

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(UtxoSignedTransactionOut);
        verify(utxoLedgerService, times(1)).finalize(UtxoSignedTransactionIn, flowSessions);
    }

    @Test
    public void receiveFinality() {
        UtxoSignedTransaction UtxoSignedTransaction = mock(UtxoSignedTransaction.class);
        FlowSession flowSession = mock(FlowSession.class);
        UtxoTransactionValidator utxoTransactionValidator = mock(UtxoTransactionValidator.class);
        when(utxoLedgerService.receiveFinality(flowSession, utxoTransactionValidator)).thenReturn(UtxoSignedTransaction);

        UtxoSignedTransaction result = utxoLedgerService.receiveFinality(flowSession, utxoTransactionValidator);

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(UtxoSignedTransaction);
        verify(utxoLedgerService, times(1)).receiveFinality(flowSession, utxoTransactionValidator);
    }
}
