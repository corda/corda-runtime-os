package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class TransactionStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getContractStateShouldReturnTheExpectedValue() {
        ContractState value = contractTransactionState.getContractState();
        Assertions.assertEquals(contractState, value);
    }

    @Test
    public void getInformationShouldReturnTheExpectedValue() {
        TransactionStateInformation value = contractTransactionState.getInformation();
        Assertions.assertEquals(transactionStateInformation, value);
    }
}
