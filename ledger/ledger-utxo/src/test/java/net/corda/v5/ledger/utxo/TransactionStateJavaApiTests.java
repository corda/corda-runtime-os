package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.common.Party;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class TransactionStateJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getContractStateShouldReturnTheExpectedValue() {
        ContractState value = contractTransactionState.getContractState();
        Assertions.assertEquals(contractState, value);
    }

    @Test
    public void getContractTypeShouldReturnTheExpectedValue() {
        Class<? extends Contract> value = contractTransactionState.getContractType();
        Assertions.assertEquals(contract.getClass(), value);
    }

    @Test
    public void getNotaryShouldReturnTheExpectedValue() {
        Party value = contractTransactionState.getNotary();
        Assertions.assertEquals(notaryParty, value);
    }

    @Test
    public void getEncumbranceShouldReturnTheExpectedValue() {
        String value = contractTransactionState.getEncumbrance();
        Assertions.assertEquals(encumbranceTag1, value);
    }
}
