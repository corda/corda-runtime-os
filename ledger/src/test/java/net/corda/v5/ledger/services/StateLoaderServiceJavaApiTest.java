package net.corda.v5.ledger.services;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.UniqueIdentifier;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.LinearPointer;
import net.corda.v5.ledger.contracts.LinearState;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.StaticPointer;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateLoaderServiceJavaApiTest {

    private final StateLoaderService stateLoaderService = mock(StateLoaderService.class);
    private final Party party = mock(Party.class);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final TestContractState contractState = new TestContractState();
    private final TransactionState<ContractState> transactionState = new TransactionState<>(contractState, party);
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final StateAndRef<ContractState> stateAndRef = new StateAndRef<>(transactionState, stateRef);
    private final UniqueIdentifier uniqueIdentifier = new UniqueIdentifier(UUID.randomUUID());
    private final LinearPointer<LinearState> linearPointer = new LinearPointer<>(uniqueIdentifier, LinearState.class);
    private final TestLinearState linearState = new TestLinearState();
    private final StaticPointer<TestContractState> staticPointer = new StaticPointer<>(stateRef, TestContractState.class);
    private final LedgerTransaction ledgerTransaction = mock(LedgerTransaction.class);

    @Test
    public void load() {
        when(stateLoaderService.load(stateRef)).thenReturn(stateAndRef);

        StateAndRef<ContractState> result = stateLoaderService.load(stateRef);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void loadWithSet() {
        Set<StateRef> stateRefSet = Set.of(stateRef);
        Set<StateAndRef<ContractState>> test = Set.of(stateAndRef);
        when(stateLoaderService.load(stateRefSet)).thenReturn(test);

        Set<StateAndRef<ContractState>> result = stateLoaderService.load(stateRefSet);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void loadOrdered() {
        List<StateRef> stateRefSet = List.of(stateRef);
        List<StateAndRef<ContractState>> test = List.of(stateAndRef);
        when(stateLoaderService.loadOrdered(stateRefSet)).thenReturn(test);

        List<StateAndRef<ContractState>> result = stateLoaderService.loadOrdered(stateRefSet);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);
    }

    @Test
    public void loadWithLinearPointer() {
        TransactionState<LinearState> transactionStateTest = new TransactionState<>(linearState, party);
        StateAndRef<LinearState> stateAndRefTest = new StateAndRef<>(transactionStateTest, stateRef);
        when(stateLoaderService.load(linearPointer)).thenReturn(stateAndRefTest);

        StateAndRef<LinearState> result = stateLoaderService.load(linearPointer);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefTest);
    }

    @Test
    public void loadWithLinearPointerAndLedgerTransaction() {
        TransactionState<LinearState> transactionStateTest = new TransactionState<>(linearState, party);
        StateAndRef<LinearState> stateAndRefTest = new StateAndRef<>(transactionStateTest, stateRef);
        when(stateLoaderService.load(linearPointer, ledgerTransaction)).thenReturn(stateAndRefTest);

        StateAndRef<LinearState> result = stateLoaderService.load(linearPointer, ledgerTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefTest);
    }

    @Test
    public void loadWithStaticPointer() {
        TransactionState<TestContractState> transactionStateTest = new TransactionState<>(contractState, party);
        StateAndRef<TestContractState> stateAndRefTest = new StateAndRef<>(transactionStateTest, stateRef);
        when(stateLoaderService.load(staticPointer)).thenReturn(stateAndRefTest);

        StateAndRef<TestContractState> result = stateLoaderService.load(staticPointer);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefTest);
    }

    @Test
    public void loadWithStaticPointerAndLedgerTransaction() {
        TransactionState<TestContractState> transactionStateTest = new TransactionState<>(contractState, party);
        StateAndRef<TestContractState> stateAndRefTest = new StateAndRef<>(transactionStateTest, stateRef);
        when(stateLoaderService.load(staticPointer, ledgerTransaction)).thenReturn(stateAndRefTest);

        StateAndRef<TestContractState> result = stateLoaderService.load(staticPointer, ledgerTransaction);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefTest);
    }

    static class TestContract implements Contract {

        @Override
        public void verify(@NotNull LedgerTransaction tx) {

        }
    }

    @BelongsToContract(TestContract.class)
    static class TestContractState implements ContractState {

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return null;
        }
    }

    @BelongsToContract(TestContract.class)
    static class TestLinearState implements LinearState {

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return null;
        }

        @NotNull
        @Override
        public UniqueIdentifier getLinearId() {
            return null;
        }
    }
}
