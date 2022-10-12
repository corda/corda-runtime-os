package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class StateAndRefJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getStateShouldReturnTheExpectedValue() {
        TransactionState<ContractState> value = contractStateAndRef.getState();
        Assertions.assertEquals(contractTransactionState, value);
    }

    @Test
    public void getRefShouldReturnTheExpectedValue() {
        StateRef value = contractStateAndRef.getRef();
        Assertions.assertEquals(stateRef, value);
    }
}
