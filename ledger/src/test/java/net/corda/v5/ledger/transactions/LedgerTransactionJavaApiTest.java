package net.corda.v5.ledger.transactions;

import kotlin.jvm.functions.Function1;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.Attachment;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.Command;
import net.corda.v5.ledger.contracts.CommandData;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.TimeWindow;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class LedgerTransactionJavaApiTest {

    private final LedgerTransaction ledgerTransaction = mock(LedgerTransaction.class);
    private final Party party = mock(Party.class);
    private final StatePredicate statePredicate = new StatePredicate();
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final PublicKey publicKey = mock(PublicKey.class);
    private final Attachment attachment = mock(Attachment.class);
    private final TestContractState testContractStateA = new TestContractState();
    private final TestContractState testContractStateB = new TestContractState();
    private final List<TestContractState> testContractStateList = List.of(testContractStateA);
    private final TransactionState<TestContractState> transactionState = new TransactionState<>(testContractStateA, party);
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final StateAndRef<TestContractState> stateAndRef = new StateAndRef<>(transactionState, stateRef);
    private final List<StateAndRef<TestContractState>> stateAndRefs = List.of(stateAndRef);
    private final Command<TestContract.Create> command = (new Command<>(new TestContract.Create(), publicKey));
    private final List<Command<TestContract.Create>> commands = List.of(command);
    private final TimeWindow timeWindow = mock(TimeWindow.class);
    private final List<TestContractState> inputs = List.of(testContractStateA);
    private final List<TestContractState> outputs = List.of(testContractStateB);
    private final LedgerTransaction.InOutGroup<TestContractState, Party> inOutGroup = new LedgerTransaction.InOutGroup<>(inputs, outputs, party);

    @Test
    public void getOutputs() {
        final List<TransactionState<TestContractState>> transactionStates = List.of(transactionState);
        doReturn(transactionStates).when(ledgerTransaction).getOutputs();

        List<TransactionState<?>> result = ledgerTransaction.getOutputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionStates);
    }

    @Test
    public void getInputStates() {
        doReturn(testContractStateList).when(ledgerTransaction).getInputStates();

        List<ContractState> result = ledgerTransaction.getInputStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void getReferenceStates() {
        doReturn(testContractStateList).when(ledgerTransaction).getReferenceStates();

        List<ContractState> result = ledgerTransaction.getReferenceStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void getCommands() {
        doReturn(commands).when(ledgerTransaction).getCommands();

        List<Command<CommandData>> result = ledgerTransaction.getCommands();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(commands);
    }

    @Test
    public void getAttachments() {
        final List<Attachment> attachments = List.of(attachment);
        doReturn(attachments).when(ledgerTransaction).getAttachments();

        List<Attachment> result = ledgerTransaction.getAttachments();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(attachments);
    }

    @Test
    public void getTimeWindow() {
        doReturn(timeWindow).when(ledgerTransaction).getTimeWindow();

        TimeWindow result = ledgerTransaction.getTimeWindow();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(timeWindow);
    }

    @Test
    public void verify() {
        ledgerTransaction.verify();
        org.mockito.Mockito.verify(ledgerTransaction, times(1)).verify();
    }

    @Test
    public void inRef() {
        doReturn(stateAndRef).when(ledgerTransaction).inRef(1);

        StateAndRef<ContractState> result = ledgerTransaction.inRef(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void groupStates() {
        final List<LedgerTransaction.InOutGroup<TestContractState, Party>> inOutGroups = List.of(inOutGroup);
        final Function1<TestContractState, Party> lambda = TestContractState::getParty;
        doReturn(inOutGroups).when(ledgerTransaction).groupStates(TestContractState.class, lambda);

        List<LedgerTransaction.InOutGroup<TestContractState, Party>> result = ledgerTransaction.groupStates(TestContractState.class, lambda);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(inOutGroups);
    }

    @Test
    public void getInput() {
        doReturn(testContractStateA).when(ledgerTransaction).getInput(1);

        ContractState result = ledgerTransaction.getInput(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateA);
    }

    @Test
    public void getReferenceInput() {
        doReturn(testContractStateA).when(ledgerTransaction).getReferenceInput(1);

        ContractState result = ledgerTransaction.getReferenceInput(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateA);
    }

    @Test
    public void inputsOfType() {
        doReturn(testContractStateList).when(ledgerTransaction).inputsOfType(TestContractState.class);

        List<TestContractState> result = ledgerTransaction.inputsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void referenceInputsOfType() {
        doReturn(testContractStateList).when(ledgerTransaction).referenceInputsOfType(TestContractState.class);

        List<TestContractState> result = ledgerTransaction.referenceInputsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void inRefsOfType() {
        doReturn(stateAndRefs).when(ledgerTransaction).inRefsOfType(TestContractState.class);

        List<StateAndRef<TestContractState>> result = ledgerTransaction.inRefsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void referenceInputRefsOfType() {
        doReturn(stateAndRefs).when(ledgerTransaction).referenceInputRefsOfType(TestContractState.class);

        List<StateAndRef<TestContractState>> result = ledgerTransaction.referenceInputRefsOfType(TestContractState.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void filterInputs() {
        doReturn(testContractStateList).when(ledgerTransaction).filterInputs(TestContractState.class, statePredicate);

        List<TestContractState> result = ledgerTransaction.filterInputs(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void filterReferenceInputs() {
        doReturn(testContractStateList).when(ledgerTransaction).filterReferenceInputs(TestContractState.class, statePredicate);

        List<TestContractState> result = ledgerTransaction.filterReferenceInputs(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateList);
    }

    @Test
    public void filterInRefs() {
        doReturn(stateAndRefs).when(ledgerTransaction).filterInRefs(TestContractState.class, statePredicate);

        List<StateAndRef<TestContractState>> result = ledgerTransaction.filterInRefs(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void filterReferenceInputRefs() {
        doReturn(stateAndRefs).when(ledgerTransaction).filterReferenceInputRefs(TestContractState.class, statePredicate);

        List<StateAndRef<TestContractState>> result = ledgerTransaction.filterReferenceInputRefs(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRefs);
    }

    @Test
    public void findInput() {
        doReturn(testContractStateA).when(ledgerTransaction).findInput(TestContractState.class, statePredicate);

        TestContractState result = ledgerTransaction.findInput(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateA);
    }

    @Test
    public void findReference() {
        doReturn(testContractStateA).when(ledgerTransaction).findReference(TestContractState.class, statePredicate);

        TestContractState result = ledgerTransaction.findReference(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testContractStateA);
    }

    @Test
    public void findInRef() {
        doReturn(stateAndRef).when(ledgerTransaction).findInRef(TestContractState.class, statePredicate);

        StateAndRef<TestContractState> result = ledgerTransaction.findInRef(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void findReferenceInputRef() {
        doReturn(stateAndRef).when(ledgerTransaction).findReferenceInputRef(TestContractState.class, statePredicate);

        StateAndRef<TestContractState> result = ledgerTransaction.findReferenceInputRef(TestContractState.class, statePredicate);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateAndRef);
    }

    @Test
    public void getCommand() {
        doReturn(command).when(ledgerTransaction).getCommand(1);

        Command<CommandData> result = ledgerTransaction.getCommand(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(command);
    }

    @Test
    public void commandsOfType() {
        doReturn(commands).when(ledgerTransaction).commandsOfType(TestContract.Create.class);

        List<Command<TestContract.Create>> result = ledgerTransaction.commandsOfType(TestContract.Create.class);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(commands);
    }

    @Test
    public void filterCommands() {
        when(ledgerTransaction.filterCommands(eq(TestContract.Create.class), any())).thenReturn(commands);

        List<Command<TestContract.Create>> result = ledgerTransaction.filterCommands(TestContract.Create.class, b -> true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(commands);
    }

    @Test
    public void findCommand() {
        doReturn(command).when(ledgerTransaction).findCommand(eq(TestContract.Create.class), any());

        Command<TestContract.Create> result = ledgerTransaction.findCommand(TestContract.Create.class, b -> true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(command);
    }

    @Test
    public void getAttachmentByIndex() {
        doReturn(attachment).when(ledgerTransaction).getAttachment(1);

        Attachment result = ledgerTransaction.getAttachment(1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(attachment);
    }

    @Test
    public void getAttachmentById() {
        doReturn(attachment).when(ledgerTransaction).getAttachment(secureHash);

        Attachment result = ledgerTransaction.getAttachment(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(attachment);
    }

    @Nested
    public class InOutGroupJavaApiTest {
        @Test
        public void getInputs() {
            List<TestContractState> result = inOutGroup.getInputs();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(inputs);
        }

        @Test
        public void getOutputs() {
            List<TestContractState> result = inOutGroup.getOutputs();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(outputs);
        }

        @Test
        public void getGroupingKey() {
            Party result = inOutGroup.getGroupingKey();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(party);
        }
    }

    @BelongsToContract(TestContract.class)
    static class TestContractState implements ContractState {
        private final Party party;

        public Party getParty() {
            return party;
        }

        TestContractState(Party party) {
            this.party = party;
        }

        TestContractState() {
            this(null);
        }

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return List.of(party);
        }
    }

    static class TestContract implements Contract {
        static class Create implements CommandData {
        }

        @Override
        public void verify(@NotNull LedgerTransaction tx) {

        }
    }

    static class StatePredicate implements Predicate<TestContractState> {

        @Override
        public boolean test(TestContractState testContractState) {
            return false;
        }

        @NotNull
        @Override
        public Predicate<TestContractState> and(@NotNull Predicate<? super TestContractState> other) {
            return Predicate.super.and(other);
        }

        @NotNull
        @Override
        public Predicate<TestContractState> negate() {
            return Predicate.super.negate();
        }

        @NotNull
        @Override
        public Predicate<TestContractState> or(@NotNull Predicate<? super TestContractState> other) {
            return Predicate.super.or(other);
        }
    }
}
