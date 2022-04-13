package net.corda.v5.ledger.transactions;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.ContractStateData;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.identity.AbstractParty;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CoreTransactionJavaApiTest {

    private final CoreTransaction coreTransaction = mock(CoreTransaction.class);
    private final TestContractState testContractState = new TestContractState();
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final List<StateRef> stateRefs = List.of(stateRef);
    private final ContractStateData<TestContractState> contractStateData = new ContractStateData<>(testContractState);

    @Test
    public void getInputs() {
        doReturn(stateRefs).when(coreTransaction).getInputs();

        List<StateRef> result = coreTransaction.getInputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getReferences() {
        doReturn(stateRefs).when(coreTransaction).getReferences();

        List<StateRef> result = coreTransaction.getReferences();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getOutputs() {
        final List<ContractStateData<TestContractState>> contractStateDates = List.of(contractStateData);
        doReturn(contractStateDates).when(coreTransaction).getOutputs();

        List<ContractStateData<ContractState>> result = coreTransaction.getOutputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(contractStateDates);
    }

    @Test
    public void getMembershipParametersHash() {
        doReturn(secureHash).when(coreTransaction).getMembershipParametersHash();

        SecureHash result = coreTransaction.getMembershipParametersHash();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash);
    }

    @BelongsToContract(TestContract.class)
    static class TestContractState implements ContractState {

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return List.of();
        }
    }

    static class TestContract implements Contract {

        @Override
        public void verify(@NotNull LedgerTransaction tx) {

        }
    }
}
