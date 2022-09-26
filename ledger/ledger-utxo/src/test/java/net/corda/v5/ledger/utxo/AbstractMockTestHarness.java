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
    protected final ContractState contractState = Mockito.mock(ContractState.class);
    protected final Contract contract = Mockito.mock(Contract.class);
    protected final UtxoLedgerTransaction utxoLedgerTransaction = Mockito.mock(UtxoLedgerTransaction.class);

    // Mocked Data
    protected final Set<PublicKey> participants = Set.of(aliceKey, bobKey);

    protected AbstractMockTestHarness() {
        initializeParties();
        initializeContractState();
        initializeContract();
    }

    private void initializeContractState() {
        Mockito.when(contractState.getParticipants()).thenReturn(participants);
    }

    private void initializeContract() {
        Mockito.doNothing().when(contract).verify(utxoLedgerTransaction);
    }
}
