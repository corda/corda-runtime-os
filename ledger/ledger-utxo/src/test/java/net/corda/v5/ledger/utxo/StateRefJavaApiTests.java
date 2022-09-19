package net.corda.v5.ledger.utxo;

import net.corda.v5.crypto.SecureHash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class StateRefJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getIndexShouldReturnTheExpectedValue() {
        int value = stateRef.getIndex();
        Assertions.assertEquals(0, value);
    }

    @Test
    public void getTransactionShouldReturnTheExpectedValue() {
        SecureHash value = stateRef.getTransactionHash();
        Assertions.assertEquals(hash, value);
    }
}
