package net.corda.v5.ledger.obsolete.flowservices;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.contracts.StateRef;
import net.corda.v5.ledger.obsolete.transactions.SignedTransaction;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlowLedgerJavaApiTest {

    private final FlowLedger flowLedger = mock(FlowLedger.class);
    private final SecureHash secureHash = SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");

    @Test
    public void waitForLedgerCommit() {
        final SignedTransaction signedTransaction = mock(SignedTransaction.class);
        when(flowLedger.waitForLedgerCommit(secureHash)).thenReturn(signedTransaction);
        SignedTransaction result = flowLedger.waitForLedgerCommit(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void waitForStateConsumption() {
        final Set<StateRef> stateRefs = Set.of(new StateRef(secureHash, 1));
        flowLedger.waitForStateConsumption(stateRefs);

        verify(flowLedger, times(1)).waitForStateConsumption(stateRefs);
    }
}
