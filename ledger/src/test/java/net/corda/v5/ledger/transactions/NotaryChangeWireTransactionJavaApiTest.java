package net.corda.v5.ledger.transactions;

import net.corda.v5.base.types.OpaqueBytes;
import net.corda.v5.ledger.crypto.TransactionDigestAlgorithmNames;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotaryChangeWireTransactionJavaApiTest {

    private final NotaryChangeWireTransaction notaryChangeWireTransaction = mock(NotaryChangeWireTransaction.class);
    private final Party party = mock(Party.class);
    private final TransactionDigestAlgorithmNames transactionDigestAlgorithmNames = new TransactionDigestAlgorithmNames();
    private final List<OpaqueBytes> opaqueBytes = List.of(new OpaqueBytes(new byte[1998]));

    @Test
    public void getNewNotary() {
        when(notaryChangeWireTransaction.getNewNotary()).thenReturn(party);

        Party result = notaryChangeWireTransaction.getNewNotary();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void getSerializedComponents() {
        when(notaryChangeWireTransaction.getSerializedComponents()).thenReturn(opaqueBytes);

        List<OpaqueBytes> result = notaryChangeWireTransaction.getSerializedComponents();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(opaqueBytes);
    }

    @Test
    public void getTransactionDigestAlgorithmNames() {
        when(notaryChangeWireTransaction.getTransactionDigestAlgorithmNames()).thenReturn(transactionDigestAlgorithmNames);

        TransactionDigestAlgorithmNames result = notaryChangeWireTransaction.getTransactionDigestAlgorithmNames();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionDigestAlgorithmNames);
    }

    @Nested
    class NotaryChangeLedgerTransactionJavaApiTest {
        private final NotaryChangeLedgerTransaction notaryChangeLedgerTransaction = mock(NotaryChangeLedgerTransaction.class);

        @Test
        public void getNewNotary() {
            when(notaryChangeLedgerTransaction.getNewNotary()).thenReturn(party);

            Party result = notaryChangeLedgerTransaction.getNewNotary();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(party);
        }
    }
}
