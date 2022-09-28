package net.corda.v5.ledger.utxo;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.transaction.Party;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
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
    protected final StateRef stateRef = Mockito.mock(StateRef.class);
    protected final UtxoLedgerTransaction utxoLedgerTransaction = Mockito.mock(UtxoLedgerTransaction.class);

    protected final StateAndRef<ContractState> contractStateAndRef = createStateAndRef(Mockito.mock(ContractState.class));
    protected final TransactionState<ContractState> contractTransactionState = contractStateAndRef.getState();
    protected final ContractState contractState = contractTransactionState.getContractState();

    // Mocked Data
    protected final Set<PublicKey> keys = Set.of(aliceKey, bobKey);
    protected final MemberX500Name aliceName = new MemberX500Name("Alice", "London", "GB");
    protected final MemberX500Name bobName = new MemberX500Name("Bob", "New York", "US");
    protected final MemberX500Name notaryName = new MemberX500Name("Notary", "Zurich", "CH");
    protected final SecureHash hash = SecureHash.parse("SHA256:0000000000000000000000000000000000000000000000000000000000000000");

    public AbstractMockTestHarness() {
        initializeIdentity();
        initializeContract();
        initializeContractState();
    }

    private void initializeIdentity() {
        Mockito.when(aliceParty.getName()).thenReturn(aliceName);
        Mockito.when(aliceParty.getOwningKey()).thenReturn(aliceKey);

        Mockito.when(bobParty.getName()).thenReturn(bobName);
        Mockito.when(bobParty.getOwningKey()).thenReturn(bobKey);

        Mockito.when(notaryParty.getName()).thenReturn(notaryName);
        Mockito.when(notaryParty.getOwningKey()).thenReturn(notaryKey);
    }

    private void initializeContract() {
        Mockito.doNothing().when(contract).verify(utxoLedgerTransaction);
    }

    private void initializeContractState() {
        Mockito.when(contractState.getParticipants()).thenReturn(keys);
    }

    protected <T extends ContractState> TransactionState<T> createTransactionState(T contractState) {
        TransactionState<T> result = Mockito.mock(TransactionState.class);

        Mockito.when(result.getContractState()).thenReturn(contractState);
        Mockito.when(result.getContractType()).thenReturn((Class) contract.getClass()); // Not sure why this needs a cast.
        Mockito.when(result.getNotary()).thenReturn(notaryParty);
        Mockito.when(result.getEncumbrance()).thenReturn(0);

        return result;
    }

    private <T extends ContractState> StateAndRef<T> createStateAndRef(T contractState) {
        StateAndRef<T> result = Mockito.mock(StateAndRef.class);

        TransactionState<T> transactionState = createTransactionState(contractState);
        Mockito.when(result.getState()).thenReturn(transactionState);
        Mockito.when(result.getRef()).thenReturn(stateRef);

        return result;
    }
}
