package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.crypto.DigitalSignature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionWithSignaturesJavaApiTest {

    private final TransactionWithSignatures transactionWithSignatures = mock(TransactionWithSignatures.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final Set<PublicKey> publicKeys = Set.of(publicKey);
    private final DigitalSignatureMetadata digitalSignatureMetadata = new DigitalSignatureMetadata(Instant.MIN, new HashMap<>());

    @Test
    public void getSigs() {
        final List<DigitalSignatureAndMetadata> digitalSignatureAndMetadata = List.of(new DigitalSignatureAndMetadata(
                new DigitalSignature.WithKey(publicKey, new byte[1998]),
                digitalSignatureMetadata
        ));
        when(transactionWithSignatures.getSigs()).thenReturn(digitalSignatureAndMetadata);

        List<DigitalSignatureAndMetadata> result = transactionWithSignatures.getSigs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMetadata);
    }

    @Test
    public void getRequiredSigningKeys() {
        when(transactionWithSignatures.getRequiredSigningKeys()).thenReturn(publicKeys);

        Set<PublicKey> result = transactionWithSignatures.getRequiredSigningKeys();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKeys);
    }

    @Test
    public void getKeyDescriptions() {
        final List<String> strings = List.of("Some string");
        when(transactionWithSignatures.getKeyDescriptions(publicKeys)).thenReturn(strings);

        List<String> result = transactionWithSignatures.getKeyDescriptions(publicKeys);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(strings);
    }

    @Test
    public void getMissingSigningKeys() {
        when(transactionWithSignatures.getMissingSigningKeys()).thenReturn(publicKeys);

        Set<PublicKey> result = transactionWithSignatures.getMissingSigningKeys();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKeys);
    }
}
