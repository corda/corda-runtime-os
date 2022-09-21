package net.corda.v5.ledger.utxo;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.application.serialization.SerializationService;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.cipher.suite.DigestService;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.transaction.Party;
import net.corda.v5.ledger.utxo.transaction.*;
import org.mockito.Mockito;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarInputStream;

public class AbstractMockTestHarness {

    // Mocked APIs
    protected final DigestService digestService = Mockito.mock(DigestService.class);
    protected final SerializationService serializationService = Mockito.mock(SerializationService.class);

    protected final PublicKey aliceKey = Mockito.mock(PublicKey.class);
    protected final PublicKey bobKey = Mockito.mock(PublicKey.class);
    protected final PublicKey notaryKey = Mockito.mock(PublicKey.class);
    protected final Party aliceParty = Mockito.mock(Party.class);
    protected final Party bobParty = Mockito.mock(Party.class);
    protected final Party notaryParty = Mockito.mock(Party.class);

    protected final Contract contract = Mockito.mock(Contract.class);
    protected final VerifiableCommand command = Mockito.mock(VerifiableCommand.class);
    protected final CommandAndSignatories<VerifiableCommand> commandAndSignatories = Mockito.mock(CommandAndSignatories.class);

    protected final UtxoTransactionBuilder utxoTransactionBuilder = Mockito.mock(UtxoTransactionBuilder.class);
    protected final UtxoWireTransaction utxoWireTransaction = Mockito.mock(UtxoWireTransaction.class);
    protected final UtxoSignedTransaction utxoSignedTransaction = Mockito.mock(UtxoSignedTransaction.class);
    protected final UtxoLedgerTransaction utxoLedgerTransaction = Mockito.mock(UtxoLedgerTransaction.class);
    protected final UtxoLedgerService utxoLedgerService = Mockito.mock(UtxoLedgerService.class);

    protected final CpkConstraint constraint = Mockito.mock(CpkConstraint.class);
    protected final CpkConstraintContext constraintContext = Mockito.mock(CpkConstraintContext.class);
    protected final Attachment attachment = Mockito.mock(Attachment.class);
    protected final InputStream inputStream = Mockito.mock(InputStream.class);
    protected final OutputStream outputStream = Mockito.mock(OutputStream.class);
    protected final JarInputStream jarInputStream = Mockito.mock(JarInputStream.class);
    protected final TimeWindow timeWindow = Mockito.mock(TimeWindow.class);

    protected final StateRef stateRef = Mockito.mock(StateRef.class);
    protected final InputOutputGroup<IdentifiableState, UUID> inputOutputGroup = Mockito.mock(InputOutputGroup.class);
    protected final TransactionStateInformation transactionStateInformation = Mockito.mock(TransactionStateInformation.class);

    protected final StateAndRef<ContractState> contractStateAndRef = createStateAndRef(Mockito.mock(ContractState.class));
    protected final StateAndRef<IdentifiableState> identifiableStateAndRef = createStateAndRef(Mockito.mock(IdentifiableState.class));
    protected final StateAndRef<FungibleState<BigDecimal>> fungibleStateStateAndRef = createStateAndRef(Mockito.mock(FungibleState.class));
    protected final StateAndRef<IssuableState> issuableStateAndRef = createStateAndRef(Mockito.mock(IssuableState.class));
    protected final StateAndRef<BearableState> bearableStateAndRef = createStateAndRef(Mockito.mock(BearableState.class));

    protected final TransactionState<ContractState> contractTransactionState = contractStateAndRef.getState();
    protected final TransactionState<IdentifiableState> identifiableTransactionState = identifiableStateAndRef.getState();
    protected final TransactionState<FungibleState<BigDecimal>> fungibleTransactionState = fungibleStateStateAndRef.getState();
    protected final TransactionState<IssuableState> issuableTransactionState = issuableStateAndRef.getState();
    protected final TransactionState<BearableState> bearableTransactionState = bearableStateAndRef.getState();

    protected final ContractState contractState = contractTransactionState.getContractState();
    protected final IdentifiableState identifiableState = identifiableTransactionState.getContractState();
    protected final FungibleState<BigDecimal> fungibleState = fungibleTransactionState.getContractState();
    protected final IssuableState issuableState = issuableTransactionState.getContractState();
    protected final BearableState bearableState = bearableTransactionState.getContractState();

    // Mocked Data
    protected final Set<PublicKey> keys = Set.of(aliceKey, bobKey);
    protected final MemberX500Name aliceName = new MemberX500Name("Alice", "London", "GB");
    protected final MemberX500Name bobName = new MemberX500Name("Bob", "London", "GB");
    protected final MemberX500Name notaryName = new MemberX500Name("Notary", "London", "GB");
    protected final UUID id = UUID.fromString("00000000-0000-4000-0000-000000000000");
    protected final BigDecimal quantity = BigDecimal.valueOf(123.45);
    protected final SecureHash hash = SecureHash.parse("SHA256:0000000000000000000000000000000000000000000000000000000000000000");
    protected final Instant minInstant = Instant.MIN;
    protected final Instant maxInstant = Instant.MAX;
    protected final Instant midpoint = Instant.EPOCH;
    protected final Duration duration = Duration.between(minInstant, maxInstant);
    protected final String contractId = "com.example.contract.id";
    protected final DigitalSignature.WithKey signature = new DigitalSignature.WithKey(aliceKey, new byte[]{0}, Map.of());
    protected final DigitalSignatureMetadata metadata = new DigitalSignatureMetadata(minInstant, Map.of());
    protected final DigitalSignatureAndMetadata signatureAndMetadata = new DigitalSignatureAndMetadata(signature, metadata);

    protected AbstractMockTestHarness() {
        initializeParties();
        initializeContractState();
        initializeIdentifiableState();
        initializeFungibleState();
        initializeIssuableState();
        initializeBearableState();
        initializeContract();
        initializeCommand();
        initializeCommandAndSignatories();
        initializeUtxoTransactionBuider();
        initializeUtxoWireTransaction();
        initializeUtxoSignedTransaction();
        initializeUtxoLedgerTransaction();
        initializeUtxoLedgerService();
        initializeConstraint();
        initializeAttachment();
        initializeTimeWindow();
        initializeTransactionStateInformation();
        initializeStateRef();
        initializeInputOutputGroup();
    }

    protected <T extends ContractState> TransactionState<T> createTransactionState(T contractState) {
        TransactionState<T> result = Mockito.mock(TransactionState.class);

        Mockito.when(result.getContractState()).thenReturn(contractState);
        Mockito.when(result.getInformation()).thenReturn(transactionStateInformation);

        return result;
    }

    protected <T extends ContractState> StateAndRef<T> createStateAndRef(T contractState) {
        StateAndRef<T> result = Mockito.mock(StateAndRef.class);

        TransactionState<T> transactionState = createTransactionState(contractState);
        Mockito.when(result.getState()).thenReturn(transactionState);
        Mockito.when(result.getRef()).thenReturn(stateRef);

        return result;
    }

    private void initializeParties() {
        Mockito.when(aliceParty.getName()).thenReturn(aliceName);
        Mockito.when(aliceParty.getOwningKey()).thenReturn(aliceKey);
        Mockito.when(bobParty.getName()).thenReturn(bobName);
        Mockito.when(bobParty.getOwningKey()).thenReturn(bobKey);
        Mockito.when(notaryParty.getName()).thenReturn(notaryName);
        Mockito.when(notaryParty.getOwningKey()).thenReturn(notaryKey);
    }

    private void initializeContractState() {
        Mockito.when(contractState.getParticipants()).thenReturn(keys);
    }

    private void initializeIdentifiableState() {
        Mockito.when(identifiableState.getId()).thenReturn(id);
        Mockito.when(identifiableState.getParticipants()).thenReturn(keys);
    }

    private void initializeFungibleState() {
        Mockito.when(fungibleState.getQuantity()).thenReturn(quantity);
        Mockito.when(fungibleState.getParticipants()).thenReturn(keys);
    }

    private void initializeIssuableState() {
        Mockito.when(issuableState.getIssuer()).thenReturn(aliceKey);
        Mockito.when(issuableState.getParticipants()).thenReturn(keys);
    }

    private void initializeBearableState() {
        Mockito.when(bearableState.getBearer()).thenReturn(bobKey);
        Mockito.when(bearableState.getParticipants()).thenReturn(keys);
    }

    private void initializeContract() {
        Mockito.doNothing().when(contract).verify(utxoLedgerTransaction);
    }

    private void initializeCommand() {
        Mockito.doNothing().when(command).verify(utxoLedgerTransaction, keys);
    }

    private void initializeCommandAndSignatories() {
        Mockito.when(commandAndSignatories.getCommand()).thenReturn(command);
        Mockito.when(commandAndSignatories.getSignatories()).thenReturn(keys);
    }

    private void initializeUtxoTransactionBuider() {
        final List<SecureHash> attachments = new ArrayList<>();
        final List<CommandAndSignatories<?>> commandsAndSignatories = new ArrayList<>();
        final List<StateAndRef<?>> inputStateAndRefs = new ArrayList<>();
        final List<StateAndRef<?>> referenceInputStateAndRefs = new ArrayList<>();
        final List<TransactionState<?>> outputStateAndRefs = new ArrayList<>();

        Mockito.when(utxoTransactionBuilder.getNotary()).thenReturn(notaryParty);
        Mockito.when(utxoTransactionBuilder.getTimeWindow()).thenReturn(timeWindow);
        Mockito.when(utxoTransactionBuilder.getAttachments()).thenReturn(attachments);
        Mockito.when(utxoTransactionBuilder.getCommands()).thenReturn(commandsAndSignatories);
        Mockito.when(utxoTransactionBuilder.getInputStateAndRefs()).thenReturn(inputStateAndRefs);
        Mockito.when(utxoTransactionBuilder.getReferenceInputStateAndRefs()).thenReturn(referenceInputStateAndRefs);
        Mockito.when(utxoTransactionBuilder.getOutputTransactionStates()).thenReturn(outputStateAndRefs);
        Mockito.when(utxoTransactionBuilder.getRequiredSignatories()).thenCallRealMethod();

        Mockito.when(utxoTransactionBuilder.addAttachment(hash)).then(invocation -> {
            attachments.add(hash);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addCommandAndSignatories(command, keys)).then(invocation -> {
            commandsAndSignatories.add(commandAndSignatories);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addCommandAndSignatories(command, aliceKey, bobKey)).thenCallRealMethod();

        Mockito.when(utxoTransactionBuilder.addInputState(contractStateAndRef)).then(invocation -> {
            inputStateAndRefs.add(contractStateAndRef);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addReferenceInputState(contractStateAndRef)).then(invocation -> {
            referenceInputStateAndRefs.add(contractStateAndRef);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractTransactionState)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractState)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractState, notaryParty)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractState, contractId)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractState, contractId, notaryParty)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.addOutputState(contractState, contractId, notaryParty, 0)).then(invocation -> {
            outputStateAndRefs.add(contractTransactionState);
            return utxoTransactionBuilder;
        });

        Mockito.when(utxoTransactionBuilder.setTimeWindowFrom(minInstant)).thenReturn(utxoTransactionBuilder);
        Mockito.when(utxoTransactionBuilder.setTimeWindowUntil(maxInstant)).thenReturn(utxoTransactionBuilder);
        Mockito.when(utxoTransactionBuilder.setTimeWindowBetween(minInstant, maxInstant)).thenReturn(utxoTransactionBuilder);
        Mockito.when(utxoTransactionBuilder.setTimeWindowBetween(midpoint, duration)).thenReturn(utxoTransactionBuilder);

        Mockito.when(utxoTransactionBuilder.sign()).thenReturn(utxoSignedTransaction);
        Mockito.when(utxoTransactionBuilder.sign(keys)).thenReturn(utxoSignedTransaction);
        Mockito.when(utxoTransactionBuilder.sign(aliceKey)).thenReturn(utxoSignedTransaction);

        Mockito.doNothing().when(utxoTransactionBuilder).verify();
        Mockito.when(utxoTransactionBuilder.verifyAndSign()).thenReturn(utxoSignedTransaction);
        Mockito.when(utxoTransactionBuilder.verifyAndSign(keys)).thenReturn(utxoSignedTransaction);
        Mockito.when(utxoTransactionBuilder.verifyAndSign(aliceKey)).thenReturn(utxoSignedTransaction);

        Mockito.when(utxoTransactionBuilder.toWireTransaction()).thenReturn(utxoWireTransaction);
    }

    private void initializeUtxoWireTransaction() {
        // TODO : Mocks will be implemented when UtxoWireTransaction has been defined.
    }

    private void initializeUtxoSignedTransaction() {
        final Set<DigitalSignatureAndMetadata> signatures = new HashSet<>();

        Mockito.when(utxoSignedTransaction.getId()).thenReturn(hash);
        Mockito.when(utxoSignedTransaction.getSignatures()).thenReturn(signatures);

        Mockito.when(utxoSignedTransaction.addSignatures(signatureAndMetadata)).then(invocation -> {
            signatures.add(signatureAndMetadata);
            return utxoSignedTransaction;
        });

        Mockito.when(utxoSignedTransaction.addSignatures(Set.of(signatureAndMetadata))).then(invocation -> {
            signatures.add(signatureAndMetadata);
            return utxoSignedTransaction;
        });

        Mockito.when(utxoSignedTransaction.getMissingSignatories(serializationService)).thenReturn(keys);
        Mockito.when(utxoSignedTransaction.toLedgerTransaction(serializationService)).thenReturn(utxoLedgerTransaction);
    }

    private void initializeUtxoLedgerService() {
        Mockito.when(utxoLedgerService.getTransactionBuilder(notaryParty)).thenReturn(utxoTransactionBuilder);
        Mockito.when(utxoLedgerService.resolve(Set.of(stateRef))).thenReturn(List.of(contractStateAndRef));
        Mockito.when(utxoLedgerService.resolve(stateRef)).thenReturn(List.of(contractStateAndRef));
        Mockito.doNothing().when(utxoLedgerService).verify(Set.of(contractStateAndRef));
        Mockito.doNothing().when(utxoLedgerService).verify(contractStateAndRef);
    }

    private void initializeUtxoLedgerTransaction() {

    }

    private void initializeConstraint() {
        Mockito.when(constraint.isSatisfiedBy(digestService, constraintContext)).thenReturn(true);
    }

    private void initializeAttachment() {
        Mockito.when(attachment.getId()).thenReturn(hash);
        Mockito.when(attachment.getSize()).thenReturn(0);
        Mockito.when(attachment.getSignatories()).thenReturn(keys);
        Mockito.doNothing().when(attachment).extractFile("", outputStream);
        Mockito.when(attachment.open()).thenReturn(inputStream);
        Mockito.when(attachment.openAsJar()).thenReturn(jarInputStream);
    }

    private void initializeTimeWindow() {
        Mockito.when(timeWindow.getFrom()).thenReturn(minInstant);
        Mockito.when(timeWindow.getUntil()).thenReturn(maxInstant);
        Mockito.when(timeWindow.getMidpoint()).thenReturn(midpoint);
        Mockito.when(timeWindow.getDuration()).thenCallRealMethod();
        Mockito.when(timeWindow.contains(midpoint)).thenReturn(true);
    }

    private void initializeTransactionStateInformation() {
        Mockito.when(transactionStateInformation.getContractId()).thenReturn(contractId);
        Mockito.when(transactionStateInformation.getNotary()).thenReturn(notaryParty);
        Mockito.when(transactionStateInformation.getEncumbrance()).thenReturn(0);
        Mockito.when(transactionStateInformation.getConstraint()).thenReturn(constraint);
    }

    private void initializeStateRef() {
        Mockito.when(stateRef.getIndex()).thenReturn(0);
        Mockito.when(stateRef.getTransactionHash()).thenReturn(hash);
    }

    private void initializeInputOutputGroup() {
        Mockito.when(inputOutputGroup.getInputs()).thenReturn(List.of(identifiableStateAndRef));
        Mockito.when(inputOutputGroup.getOutputs()).thenReturn(List.of(identifiableStateAndRef));
        Mockito.when(inputOutputGroup.getGroupingKey()).thenReturn(id);
    }
}
