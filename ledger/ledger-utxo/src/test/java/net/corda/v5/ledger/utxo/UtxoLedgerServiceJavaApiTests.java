package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public final class UtxoLedgerServiceJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getTransactionBuilderShouldReturnTheExpectedResult() {
        UtxoTransactionBuilder value = utxoLedgerService.getTransactionBuilder(notaryParty);
        Assertions.assertEquals(utxoTransactionBuilder, value);
    }

    @Test
    public void resolveShouldReturnTheExpectedResult() {
        List<StateAndRef<ContractState>> value = utxoLedgerService.resolve(Set.of(stateRef));
        Assertions.assertEquals(List.of(contractStateAndRef), value);
    }

    @Test
    public void resolveVarargShouldReturnTheExpectedResult() {
        List<StateAndRef<ContractState>> value = utxoLedgerService.resolve(stateRef);
        Assertions.assertEquals(List.of(contractStateAndRef), value);
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
