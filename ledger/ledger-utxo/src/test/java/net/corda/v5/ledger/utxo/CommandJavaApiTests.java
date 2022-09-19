package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Test;

public final class CommandJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void verifyShouldBeCallable() {
        command.verify(utxoLedgerTransaction, keys);
    }
}
