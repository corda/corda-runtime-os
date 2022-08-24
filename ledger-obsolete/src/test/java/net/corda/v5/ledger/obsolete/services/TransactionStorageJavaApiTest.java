package net.corda.v5.ledger.obsolete.services;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.transactions.SignedTransaction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionStorageJavaApiTest {
    private final TransactionStorage transactionStorage = mock(TransactionStorage.class);
    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);

    @Test
    public void getTransaction() {
        SecureHash secureHash = SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
        when(transactionStorage.getTransaction(secureHash)).thenReturn(signedTransaction);

        SignedTransaction result = transactionStorage.getTransaction(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }
}
