package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMeta;
import net.corda.v5.application.crypto.SignatureMetadata;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.mock;

public class SignedTransactionDigestJavaApiTest {

    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<String> stringListA = List.of("Some strings");
    private final List<String> stringListB = List.of("Other strings");
    private final PublicKey publicKey = mock(PublicKey.class);
    private final SignatureMetadata signatureMetadata = new SignatureMetadata(9);
    private final List<DigitalSignatureAndMeta> digitalSignatureAndMeta = List.of(new DigitalSignatureAndMeta(new byte[1998], publicKey, signatureMetadata));
    private final SignedTransactionDigest signedTransactionDigestA = new SignedTransactionDigest(secureHash, stringListA, digitalSignatureAndMeta);
    private final SignedTransactionDigest signedTransactionDigestB = new SignedTransactionDigest(secureHash.toString(), stringListA, stringListB);

    @Test
    public void toJsonString() {
        String result = signedTransactionDigestA.toJsonString();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).contains("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
        Assertions.assertThat(result).contains("Some strings");
    }

    @Test
    public void getTxId() {
        String result = signedTransactionDigestA.getTxId();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    }

    @Test
    public void getOutputStates() {
        List<String> result = signedTransactionDigestA.getOutputStates();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stringListA);
    }

    @Test
    public void getSignatures() {
        List<String> result = signedTransactionDigestB.getSignatures();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(stringListB);
    }
}
