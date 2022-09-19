package net.corda.v5.ledger.utxo;

import net.corda.v5.cipher.suite.DigestService;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;

public class AbstractMockTestHarness {

    // Mocked APIs
    protected final PublicKey aliceKey = Mockito.mock(PublicKey.class);
    protected final PublicKey bobKey = Mockito.mock(PublicKey.class);
    protected final ContractState contractState = Mockito.mock(ContractState.class);
    protected final IdentifiableState identifiableState = Mockito.mock(IdentifiableState.class);
    protected final FungibleState<BigDecimal> fungibleState = Mockito.mock(FungibleState.class);
    protected final IssuableState issuableState = Mockito.mock(IssuableState.class);
    protected final BearableState bearableState = Mockito.mock(BearableState.class);
    protected final Contract contract = Mockito.mock(Contract.class);
    protected final VerifiableCommand command = Mockito.mock(VerifiableCommand.class);
    protected final CommandAndSignatories<VerifiableCommand> commandAndSignatories = Mockito.mock(CommandAndSignatories.class);
    protected final UtxoLedgerTransaction utxoLedgerTransaction = Mockito.mock(UtxoLedgerTransaction.class);
    protected final CpkConstraint constraint = Mockito.mock(CpkConstraint.class);
    protected final CpkConstraintContext constraintContext = Mockito.mock(CpkConstraintContext.class);
    protected final DigestService digestService = Mockito.mock(DigestService.class);

    // Mocked Data
    protected final Set<PublicKey> participants = Set.of(aliceKey, bobKey);
    protected final UUID id = UUID.fromString("00000000-0000-4000-0000-000000000000");
    protected final BigDecimal quantity = BigDecimal.valueOf(123.45);

    protected AbstractMockTestHarness() {
        initializeContractState();
        initializeIdentifiableState();
        initializeFungibleState();
        initializeIssuableState();
        initializeBearableState();
        initializeContract();
        initializeCommand();
        initializeCommandAndSignatories();
        initializeConstraint();
    }

    private void initializeContractState() {
        Mockito.when(contractState.getParticipants()).thenReturn(participants);
    }

    private void initializeIdentifiableState() {
        Mockito.when(identifiableState.getId()).thenReturn(id);
        Mockito.when(identifiableState.getParticipants()).thenReturn(participants);
    }

    private void initializeFungibleState() {
        Mockito.when(fungibleState.getQuantity()).thenReturn(quantity);
        Mockito.when(fungibleState.getParticipants()).thenReturn(participants);
    }

    private void initializeIssuableState() {
        Mockito.when(issuableState.getIssuer()).thenReturn(aliceKey);
        Mockito.when(issuableState.getParticipants()).thenReturn(participants);
    }

    private void initializeBearableState() {
        Mockito.when(bearableState.getBearer()).thenReturn(bobKey);
        Mockito.when(bearableState.getParticipants()).thenReturn(participants);
    }

    private void initializeContract() {
        Mockito.doNothing().when(contract).verify(utxoLedgerTransaction);
    }

    private void initializeCommand() {
        Mockito.doNothing().when(command).verify(utxoLedgerTransaction, participants);
    }

    private void initializeCommandAndSignatories() {
        Mockito.when(commandAndSignatories.getCommand()).thenReturn(command);
        Mockito.when(commandAndSignatories.getSignatories()).thenReturn(participants);
    }

    private void initializeConstraint() {
        Mockito.when(constraint.isSatisfiedBy(digestService, constraintContext)).thenReturn(true);
    }
}
