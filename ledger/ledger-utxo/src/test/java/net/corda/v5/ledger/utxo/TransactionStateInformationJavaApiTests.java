package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.common.transaction.Party;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class TransactionStateInformationJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getContractIdShouldReturnTheExpectedValue() {
        String value = transactionStateInformation.getContractId();
        Assertions.assertEquals(contractId, value);
    }

    @Test
    public void getNotaryShouldReturnTheExpectedValue() {
        Party value = transactionStateInformation.getNotary();
        Assertions.assertEquals(notaryParty, value);
    }

    @Test
    public void getEncumbranceShouldReturnTheExpectedValue() {
        int value = transactionStateInformation.getEncumbrance();
        Assertions.assertEquals(0, value);
    }

    @Test
    public void getConstraintShouldReturnTheExpectedValue() {
        CpkConstraint value = transactionStateInformation.getConstraint();
        Assertions.assertEquals(constraint, value);
    }
}
