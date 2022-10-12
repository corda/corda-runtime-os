package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public final class UtxoLedgerServiceJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getTransactionBuilderShouldReturnTheExpectedResult() {
        UtxoTransactionBuilder value = utxoLedgerService.getTransactionBuilder();
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void resolvePluralShouldReturnTheExpectedResult() {
        List<StateAndRef<ContractState>> value = utxoLedgerService.resolve(List.of(stateRef));
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void resolveSingularShouldReturnTheExpectedResult() {
        StateAndRef<ContractState> value = utxoLedgerService.resolve(stateRef);
        Assertions.assertEquals(contractStateAndRef, value);
    }

    @Test
    public void verifyShouldBeCallable() {
        utxoLedgerService.verify(Set.of(contractStateAndRef));
    }

    @Test
    public void verifyVarargShouldBeCallable() {
        utxoLedgerService.verify(contractStateAndRef);
    }
}
