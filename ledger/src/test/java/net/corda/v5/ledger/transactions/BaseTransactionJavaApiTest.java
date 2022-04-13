package net.corda.v5.ledger.transactions;

import kotlin.Pair;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.ClassInfo;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.PackageIdWithDependencies;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateInfo;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BaseTransactionJavaApiTest {

    private final BaseTransaction baseTransaction = mock(BaseTransaction.class);
    private final Party party = mock(Party.class);
    private final List<Integer> dummyInteger = List.of(1, 2, 3, 4, 5);
    private final ClassInfo classInfo = new ClassInfo("abundle", "1.0.0", "aClassName");
    private final List<ClassInfo> classInfos = List.of(classInfo);
    private final SecureHash secureHashA = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final TestContractState testContractState = new TestContractState();
    private final List<TestContractState> testContractStateList = List.of(testContractState);
    private final TransactionState<TestContractState> transactionState = new TransactionState<>(testContractState, party);
    private final StateRef stateRef = new StateRef(secureHashA, 1);
    private final StateAndRef<TestContractState> stateAndRef = new StateAndRef<>(transactionState, stateRef);
    private final List<StateAndRef<TestContractState>> stateAndRefs = List.of(stateAndRef);

    @Test
    public void getReferences() {
        doReturn(dummyInteger).when(baseTransaction).getReferences();

        List<?> result = baseTransaction.getReferences();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(dummyInteger);
    }

    @Test
    public void getInputs() {
        doReturn(dummyInteger).when(baseTransaction).getInputs();

        List<?> result = baseTransaction.getInputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(dummyInteger);
    }

    @Test
    public void getOutputs() {
        doReturn(dummyInteger).when(baseTransaction).getOutputs();

        List<?> result = baseTransaction.getOutputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(dummyInteger);
    }

    @Test
    public void getNotary() {
        when(baseTransaction.getNotary()).thenReturn(party);

        Party result = baseTransaction.getNotary();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void getTransactionParameters() {
        List<Pair<String, String>> transactionParameters = List.of(new Pair<>("key", "value"));
        doReturn(transactionParameters).when(baseTransaction).getTransactionParameters();

        List<Pair<String, String>> result = baseTransaction.getTransactionParameters();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionParameters);
    }

    @Test
    public void getPackages() {
        final SecureHash secureHashB = SecureHash.create("SHA-256:6B1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581B");
        List<PackageIdWithDependencies> dependencies = List.of(new PackageIdWithDependencies(secureHashA, List.of(secureHashB)));
        doReturn(dependencies).when(baseTransaction).getPackages();

        List<PackageIdWithDependencies> result = baseTransaction.getPackages();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(dependencies);
    }

    @Test
    public void getInputsMetaData() {
        doReturn(classInfos).when(baseTransaction).getInputsMetaData();

        List<ClassInfo> result = baseTransaction.getInputsMetaData();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(classInfos);
    }

    @Test
    public void getOutputsData() {
        final List<StateInfo> stateInfos = List.of(new StateInfo("contractClassMame", party));
        doReturn(stateInfos).when(baseTransaction).getOutputsData();

        List<StateInfo> result = baseTransaction.getOutputsData();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateInfos);
    }

    @Test
    public void getCommandsMetaData() {
        doReturn(classInfos).when(baseTransaction).getCommandsMetaData();

        List<ClassInfo> result = baseTransaction.getCommandsMetaData();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(classInfos);
    }

    @Test
    public void getReferencesMetaData() {
        doReturn(classInfos).when(baseTransaction).getReferencesMetaData();

        List<ClassInfo> result = baseTransaction.getReferencesMetaData();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(classInfos);
    }

    @Test
    public void checkBaseInvariants() {
        baseTransaction.checkBaseInvariants();
        verify(baseTransaction, times(1)).checkBaseInvariants();
    }

    @Test
    public void outRefIndex() {
        doReturn(stateAndRef).when(baseTransaction).outRef(1);

        StateAndRef<ContractState> result = baseTransaction.outRef(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void outRefState() {
        doReturn(stateAndRef).when(baseTransaction).outRef(testContractState);

        StateAndRef<ContractState> result = baseTransaction.outRef(testContractState);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void getOutputStates() {
        doReturn(testContractStateList).when(baseTransaction).getOutputStates();

        List<ContractState> result = baseTransaction.getOutputStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void asContractState() {
        doReturn(testContractState).when(baseTransaction).asContractState(1);

        ContractState result = baseTransaction.asContractState(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractState);
    }

    @Test
    public void getOutput() {
        doReturn(testContractState).when(baseTransaction).getOutput(1);

        ContractState result = baseTransaction.getOutput(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractState);
    }

    @Test
    public void outputsOfType() {
        doReturn(testContractStateList).when(baseTransaction).outputsOfType(TestContractState.class);

        List<TestContractState> result = baseTransaction.outputsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void filterOutputs() {
        doReturn(testContractStateList).when(baseTransaction).filterOutputs(eq(TestContractState.class), any());

        List<TestContractState> result = baseTransaction.filterOutputs(TestContractState.class, n -> n.getParticipants().isEmpty());

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void findOutput() {
        doReturn(testContractState).when(baseTransaction).findOutput(eq(TestContractState.class), any());

        TestContractState result = baseTransaction.findOutput(TestContractState.class, n -> n.getParticipants().isEmpty());

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractState);
    }

    @Test
    public void outRefsOfType() {
        doReturn(stateAndRefs).when(baseTransaction).outRefsOfType(TestContractState.class);

        List<StateAndRef<TestContractState>> result = baseTransaction.outRefsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void filterOutRefs() {
        doReturn(stateAndRefs).when(baseTransaction).filterOutRefs(eq(TestContractState.class), any());

        List<StateAndRef<TestContractState>> result =
                baseTransaction.filterOutRefs(TestContractState.class, n -> n.getParticipants().isEmpty());

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void findOutRef() {
        doReturn(stateAndRef).when(baseTransaction).findOutRef(eq(TestContractState.class), any());

        StateAndRef<TestContractState> result = baseTransaction.findOutRef(TestContractState.class, n -> n.getParticipants().isEmpty());

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
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
