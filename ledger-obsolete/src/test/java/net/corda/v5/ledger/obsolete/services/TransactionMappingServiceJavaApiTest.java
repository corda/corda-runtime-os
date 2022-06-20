package net.corda.v5.ledger.obsolete.services;

import net.corda.v5.ledger.obsolete.transactions.LedgerTransaction;
import net.corda.v5.ledger.obsolete.transactions.SignedTransaction;
import net.corda.v5.ledger.obsolete.transactions.WireTransaction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.SignatureException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionMappingServiceJavaApiTest {

    private final TransactionMappingService transactionMappingService = mock(TransactionMappingService.class);
    private final LedgerTransaction ledgerTransaction = mock(LedgerTransaction.class);
    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);
    private final WireTransaction wireTransaction = mock(WireTransaction.class);

    @Test
    public void toLedgerTransactionWithSignedTransactionAndCheckSufficientSignatures() throws SignatureException {
        when(transactionMappingService.toLedgerTransaction(signedTransaction, true)).thenReturn(ledgerTransaction);

        LedgerTransaction result = transactionMappingService.toLedgerTransaction(signedTransaction, true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(ledgerTransaction);
    }

    @Test
    public void toLedgerTransactionWithSignedTransaction() throws SignatureException {
        when(transactionMappingService.toLedgerTransaction(signedTransaction)).thenReturn(ledgerTransaction);

        LedgerTransaction result = transactionMappingService.toLedgerTransaction(signedTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(ledgerTransaction);
    }

    @Test
    public void toLedgerTransactionWithWireTransaction() {
        when(transactionMappingService.toLedgerTransaction(wireTransaction)).thenReturn(ledgerTransaction);

        LedgerTransaction result = transactionMappingService.toLedgerTransaction(wireTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(ledgerTransaction);
    }
}
