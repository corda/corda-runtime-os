package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.identity.Party;
import net.corda.v5.serialization.SerializedBytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignedTransactionJavaApiTest {

    private final SignedTransaction signedTransaction = mock(SignedTransaction.class);
    private final CoreTransaction coreTransaction = mock(CoreTransaction.class);
    private final WireTransaction wireTransaction = mock(WireTransaction.class);
    private final FilteredTransaction filteredTransaction = mock(FilteredTransaction.class);
    private final byte[] bytes = new byte[1998];
    private final SerializedBytes<CoreTransaction> serializedBytes = new SerializedBytes<>(bytes);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final StateRef stateRef = new StateRef(secureHash, 1);
    private final List<StateRef> stateRefs = List.of(stateRef);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final Party party = mock(Party.class);
    private final DigitalSignatureMetadata digitalSignatureMetadata = new DigitalSignatureMetadata(Instant.MIN, new HashMap<>());
    private final DigitalSignatureAndMetadata digitalSignatureAndMetadata = new DigitalSignatureAndMetadata(
            new DigitalSignature.WithKey(publicKey, bytes),
            digitalSignatureMetadata
    );

    @Test
    public void getTxBits() {
        when(signedTransaction.getTxBits()).thenReturn(serializedBytes);

        SerializedBytes<CoreTransaction> result = signedTransaction.getTxBits();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(serializedBytes);
    }

    @Test
    public void getCoreTransaction() {
        when(signedTransaction.getCoreTransaction()).thenReturn(coreTransaction);

        CoreTransaction result = signedTransaction.getCoreTransaction();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(coreTransaction);
    }

    @Test
    public void getTx() {
        when(signedTransaction.getTx()).thenReturn(wireTransaction);

        WireTransaction result = signedTransaction.getTx();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(wireTransaction);
    }

    @Test
    public void buildFilteredTransaction() {
        when(signedTransaction.buildFilteredTransaction(any())).thenReturn(filteredTransaction);

        FilteredTransaction result = signedTransaction.buildFilteredTransaction(b -> true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(filteredTransaction);
    }

    @Test
    public void getInputs() {
        when(signedTransaction.getInputs()).thenReturn(stateRefs);

        List<StateRef> result = signedTransaction.getInputs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getReferences() {
        when(signedTransaction.getReferences()).thenReturn(stateRefs);

        List<StateRef> result = signedTransaction.getReferences();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stateRefs);
    }

    @Test
    public void getNotary() {
        when(signedTransaction.getNotary()).thenReturn(party);

        Party result = signedTransaction.getNotary();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(party);
    }

    @Test
    public void getGroupParametersHash() {
        when(signedTransaction.getGroupParametersHash()).thenReturn(secureHash);

        SecureHash result = signedTransaction.getGroupParametersHash();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash);
    }

    @Test
    public void withAdditionalSignature() {
        when(signedTransaction.withAdditionalSignature(digitalSignatureAndMetadata)).thenReturn(signedTransaction);

        SignedTransaction result = signedTransaction.withAdditionalSignature(digitalSignatureAndMetadata);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }

    @Test
    public void withAdditionalSignatures() {
        final List<DigitalSignatureAndMetadata> digitalSignatureAndMetadata = List.of(this.digitalSignatureAndMetadata);
        when(signedTransaction.withAdditionalSignatures(digitalSignatureAndMetadata)).thenReturn(signedTransaction);

        SignedTransaction result = signedTransaction.withAdditionalSignatures(digitalSignatureAndMetadata);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(signedTransaction);
    }
}
