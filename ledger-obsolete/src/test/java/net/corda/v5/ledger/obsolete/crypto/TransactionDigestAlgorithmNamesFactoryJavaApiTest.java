package net.corda.v5.ledger.obsolete.crypto;

import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.DigestService;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.merkle.MerkleTree;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.corda.v5.crypto.DigestAlgorithmName.SHA2_256;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;

public class TransactionDigestAlgorithmNamesFactoryJavaApiTest {

    private final TransactionDigestAlgorithmNames transactionDigestAlgorithmNames = new TransactionDigestAlgorithmNames();
    private final DigestService digestService = mock(DigestService.class);
    private final byte[] bytes = new byte[1998];
    private final DigestAlgorithmName digestAlgorithmName = DigestAlgorithmName.SHA2_256;
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<SecureHash> secureHashes = List.of(secureHash);

    @Test
    public void hash() {
        when(digestService.hash(bytes, digestAlgorithmName)).thenReturn(secureHash);
        SecureHash result = transactionDigestAlgorithmNames.hash(bytes, digestService);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash);
    }

    @Test
    public void getMerkleTree() {
        when(digestService.hash(any(byte[].class), eq(digestAlgorithmName))).thenReturn(secureHash);
        when(digestService.digestLength(SHA2_256)).thenReturn(1);
        MerkleTree result = transactionDigestAlgorithmNames.getMerkleTree(secureHashes, digestService);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getHash()).isEqualTo(secureHash);
    }

    @Test
    public void isAlgorithmSupported() {
        boolean result = transactionDigestAlgorithmNames.isAlgorithmSupported("SHA-256");

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isTrue();
    }
}
