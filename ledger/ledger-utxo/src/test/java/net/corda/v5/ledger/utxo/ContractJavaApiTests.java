package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Test;

public final class ContractJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void verifyShouldBeCallable() {
        contract.verify(utxoLedgerTransaction);
    }
}
