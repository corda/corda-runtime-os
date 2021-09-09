package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMeta;
import net.corda.v5.application.crypto.SignatureMetadata;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionWithSignaturesJavaApiTest {

    private final TransactionWithSignatures transactionWithSignatures = mock(TransactionWithSignatures.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final Set<PublicKey> publicKeys = Set.of(publicKey);
    private final SignatureMetadata signatureMetadata = new SignatureMetadata(9);

    @Test
    public void getSigs() {
        final List<DigitalSignatureAndMeta> digitalSignatureAndMetas = List.of(new DigitalSignatureAndMeta(new byte[1998], publicKey, signatureMetadata));
        when(transactionWithSignatures.getSigs()).thenReturn(digitalSignatureAndMetas);

        List<DigitalSignatureAndMeta> result = transactionWithSignatures.getSigs();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(digitalSignatureAndMetas);
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
