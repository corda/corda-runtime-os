package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.mockito.Mockito.mock;

public class SignedTransactionDigestJavaApiTest {

    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<String> stringListA = List.of("Some strings");
    private final List<String> stringListB = List.of("Other strings");
    private final PublicKey publicKey = mock(PublicKey.class);
    private final DigitalSignatureMetadata digitalSignatureMetadata = new DigitalSignatureMetadata(Instant.MIN, new HashMap<>());
    private final List<DigitalSignatureAndMetadata> digitalSignatureAndMetadata = List.of(new DigitalSignatureAndMetadata(
            new DigitalSignature.WithKey(publicKey, new byte[1998]),
            digitalSignatureMetadata
    ));
    private final SignedTransactionDigest signedTransactionDigestA = new SignedTransactionDigest(secureHash, stringListA, digitalSignatureAndMetadata);
    private final SignedTransactionDigest signedTransactionDigestB = new SignedTransactionDigest(secureHash.toString(), stringListA, stringListB);

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
