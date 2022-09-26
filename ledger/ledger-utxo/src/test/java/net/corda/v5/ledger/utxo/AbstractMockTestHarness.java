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
    }

    private void initializeContractState() {
        Mockito.when(contractState.getParticipants()).thenReturn(keys);
    }
}
