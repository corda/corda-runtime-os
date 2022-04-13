package net.corda.v5.ledger.transactions;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import net.corda.v5.membership.GroupParameters;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class FullTransactionJavaApiTest {

    private final FullTransaction fullTransaction = mock(FullTransaction.class);
    private final GroupParameters groupParameters = mock(GroupParameters.class);
    private final Party party = mock(Party.class);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final TestContractState testContractState = new TestContractState();
    private final TransactionState<TestContractState> transactionState = new TransactionState<>(testContractState, party);
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final List<StateAndRef<TestContractState>> stateAndRef = List.of(new StateAndRef<>(transactionState, stateRef));

    @Test
    public void getInputs() {
        doReturn(stateAndRef).when(fullTransaction).getInputs();

        List<StateAndRef<ContractState>> result = fullTransaction.getInputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void getReferences() {
        doReturn(stateAndRef).when(fullTransaction).getReferences();

        List<StateAndRef<ContractState>> result = fullTransaction.getReferences();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void getOutputs() {
        final List<TransactionState<TestContractState>> transactionStates = List.of(transactionState);
        doReturn(transactionStates).when(fullTransaction).getOutputs();

        List<TransactionState<ContractState>> result = fullTransaction.getOutputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionStates);
    }

    @Test
    public void getMembershipParameters() {
        doReturn(groupParameters).when(fullTransaction).getMembershipParameters();

        GroupParameters result = fullTransaction.getMembershipParameters();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(groupParameters);
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
