package net.corda.v5.ledger.transactions;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.BelongsToContract;
import net.corda.v5.ledger.contracts.CPKConstraint;
import net.corda.v5.ledger.contracts.Command;
import net.corda.v5.ledger.contracts.CommandData;
import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.ReferencedStateAndRef;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.TimeWindow;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransactionBuilderJavaApiTest {

    private final TransactionBuilder builderA = mock(TransactionBuilder.class);
    private final TransactionBuilder builderB = mock(TransactionBuilder.class);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final List<StateRef> stateRefs = List.of(stateRef);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final PublicKey publicKeyA = mock(PublicKey.class);
    private final PublicKey publicKeyB = mock(PublicKey.class);
    private final List<PublicKey> publicKeys = List.of(publicKey, publicKeyA, publicKeyB);
    private final TestContract.Create create = new TestContract.Create();
    private final Command<TestContract.Create> command = new Command<>(create, publicKey);
    private final List<Command<TestContract.Create>> commands = List.of(command);
    private final Party party = mock(Party.class);
    private final TestContractState testContractState = new TestContractState();
    private final TransactionState<TestContractState> transactionState = new TransactionState<>(testContractState, party);
    private final UUID uuid = UUID.randomUUID();
    private final WireTransaction wireTransaction = mock(WireTransaction.class);
    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);
    private final StateAndRef<TestContractState> stateAndRef = new StateAndRef<>(transactionState, stateRef);
    private final ReferencedStateAndRef<TestContractState> referencedStateAndRef = new ReferencedStateAndRef<>(stateAndRef);
    private final CPKConstraint cpkConstrainct = mock(CPKConstraint.class);
    private final TimeWindow timeWindow = mock(TimeWindow.class);
    private final Instant instant = Instant.MAX;
    private final Duration duration = Duration.ZERO;

    @Test
    public void getInputStates() {
        when(builderA.getInputStates()).thenReturn(stateRefs);

        List<StateRef> result = builderA.getInputStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getReferenceStates() {
        when(builderA.getReferenceStates()).thenReturn(stateRefs);

        List<StateRef> result = builderA.getReferenceStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getAttachments() {
        final List<SecureHash> secureHashes = List.of(secureHash);
        when(builderA.getAttachments()).thenReturn(secureHashes);

        List<SecureHash> result = builderA.getAttachments();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashes);
    }

    @Test
    public void getOutputStates() {
        final List<TransactionState<TestContractState>> transactionStates = List.of(transactionState);
        doReturn(transactionStates).when(builderA).getOutputStates();

        List<TransactionState<?>> result = builderA.getOutputStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(transactionStates);
    }

    @Test
    public void getCommands() {
        doReturn(commands).when(builderA).getCommands();

        List<Command<?>> result = builderA.getCommands();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(commands);
    }


    @Test
    public void getNotary() {
        when(builderA.getNotary()).thenReturn(party);

        Party result = builderA.getNotary();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void getLockId() {
        when(builderA.getLockId()).thenReturn(uuid);

        UUID result = builderA.getLockId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(uuid);
    }

    @Test
    public void copy() {
        when(builderA.copy()).thenReturn(builderB);

        TransactionBuilder result = builderA.copy();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void setNotary() {
        when(builderA.setNotary(party)).thenReturn(builderB);

        TransactionBuilder result = builderA.setNotary(party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void withItems() {
        when(builderA.withItems(stateAndRef)).thenReturn(builderB);

        TransactionBuilder result = builderA.withItems(stateAndRef);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void toWireTransaction() {
        when(builderA.toWireTransaction()).thenReturn(wireTransaction);

        WireTransaction result = builderA.toWireTransaction();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(wireTransaction);
    }

    @Test
    public void verifyBuilder() {
        builderA.verify();
        verify(builderA, times(1)).verify();
    }

    @Test
    public void signCollectionPublicKey() {
        when(builderA.sign(publicKeys)).thenReturn(signedTransaction);

        SignedTransaction result = builderA.sign(publicKeys);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void signPublicKey() {
        when(builderA.sign(publicKey)).thenReturn(signedTransaction);

        SignedTransaction result = builderA.sign(publicKey);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void sign() {
        when(builderA.sign()).thenReturn(signedTransaction);

        SignedTransaction result = builderA.sign();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void addAttachment() {
        when(builderA.addAttachment(secureHash)).thenReturn(builderB);

        TransactionBuilder result = builderA.addAttachment(secureHash);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addInputState() {
        when(builderA.addInputState(stateAndRef)).thenReturn(builderB);

        TransactionBuilder result = builderA.addInputState(stateAndRef);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addReferenceState() {
        when(builderA.addReferenceState(referencedStateAndRef)).thenReturn(builderB);

        TransactionBuilder result = builderA.addReferenceState(referencedStateAndRef);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputState() {
        when(builderA.addOutputState(transactionState)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(transactionState);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateTransactionState() {
        when(builderA.addOutputState(transactionState)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(transactionState);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateContractClassNamePartyIntCPKConstraint() {
        when(builderA.addOutputState(testContractState, "ContractClassName", party, 1, cpkConstrainct)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, "ContractClassName", party, 1, cpkConstrainct);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateContractClassNamePartyInt() {
        when(builderA.addOutputState(testContractState, "ContractClassName", party, 1)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, "ContractClassName", party, 1);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateContractClassNameParty() {
        when(builderA.addOutputState(testContractState, "ContractClassName", party)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, "ContractClassName", party);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateContractClassName() {
        when(builderA.addOutputState(testContractState, "ContractClassName")).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, "ContractClassName");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractState() {
        when(builderA.addOutputState(testContractState)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateContractCPKConstraint() {
        when(builderA.addOutputState(testContractState, "ContractClassName", cpkConstrainct)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, "ContractClassName", cpkConstrainct);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addOutputStateContractStateCPKConstraint() {
        when(builderA.addOutputState(testContractState, cpkConstrainct)).thenReturn(builderB);

        TransactionBuilder result = builderA.addOutputState(testContractState, cpkConstrainct);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addCommandCommand() {
        when(builderA.addCommand(command)).thenReturn(builderB);

        TransactionBuilder result = builderA.addCommand(command);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addCommandCommandDataPublicKeyVararg() {
        when(builderA.addCommand(create, publicKey, publicKeyA, publicKeyB)).thenReturn(builderB);

        TransactionBuilder result = builderA.addCommand(create, publicKey, publicKeyA, publicKeyB);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void addCommandListPublicKey() {
        when(builderA.addCommand(create, publicKeys)).thenReturn(builderB);

        TransactionBuilder result = builderA.addCommand(create, publicKeys);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void setTimeWindowTimeWindow() {
        when(builderA.setTimeWindow(timeWindow)).thenReturn(builderB);

        TransactionBuilder result = builderA.setTimeWindow(timeWindow);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void setTimeWindowInstantDuration() {
        when(builderA.setTimeWindow(instant, duration)).thenReturn(builderB);

        TransactionBuilder result = builderA.setTimeWindow(instant, duration);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
    }

    @Test
    public void setPrivacySalt() {
        byte[] bytes = "6D1687C143DF792A011A1E80670A4E4E".getBytes();
        final PrivacySalt privacySaltA = new PrivacySalt(bytes);
        when(builderA.setPrivacySalt(privacySaltA)).thenReturn(builderB);

        TransactionBuilder result = builderA.setPrivacySalt(privacySaltA);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(builderB);
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

        static class Create implements CommandData {
        }

        @Override
        public void verify(@NotNull LedgerTransaction tx) {

        }
    }
}
