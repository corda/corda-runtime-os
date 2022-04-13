package net.corda.v5.ledger.transactions;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.application.crypto.DigitalSignatureMetadata;
import net.corda.v5.application.crypto.DigitalSignatureVerificationService;
import net.corda.v5.base.types.OpaqueBytes;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.MerkleTree;
import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WireTransactionJavaApiTest {

    private final WireTransaction wireTransaction = mock(WireTransaction.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final List<SecureHash> secureHashes = List.of(secureHash);
    private final Map<Integer, List<SecureHash>> availableComponent = Map.of(1, secureHashes);

    private final static Integer PRIVACY_SALT_MINIMUM_SIZE = 32;

    @Test
    public void getPrivacySalt() {
        byte[] bytes = new byte[PRIVACY_SALT_MINIMUM_SIZE];
        bytes[0] = 1;
        final PrivacySalt privacySalt = new PrivacySalt(bytes);
        doReturn(privacySalt).when(wireTransaction).getPrivacySalt();

        PrivacySalt result = wireTransaction.getPrivacySalt();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(privacySalt);
    }

    @Test
    public void getRequiredSigningKeys() {
        final Set<PublicKey> publicKeySet = Set.of(publicKey);
        doReturn(publicKeySet).when(wireTransaction).getRequiredSigningKeys();

        Set<PublicKey> result = wireTransaction.getRequiredSigningKeys();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKeySet);
    }

    @Test
    public void buildFilteredTransaction() {
        final FilteredTransaction filteredTransaction = mock(FilteredTransaction.class);
        doReturn(filteredTransaction).when(wireTransaction).buildFilteredTransaction(any());

        FilteredTransaction result = wireTransaction.buildFilteredTransaction(b -> true);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(filteredTransaction);
    }

    @Test
    public void getMerkleTree() {
        final MerkleTree merkleTree = mock(MerkleTree.class);
        doReturn(merkleTree).when(wireTransaction).getMerkleTree();

        MerkleTree result = wireTransaction.getMerkleTree();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(merkleTree);
    }

    @Test
    public void getGroupHashes() {
        doReturn(secureHashes).when(wireTransaction).getGroupHashes();

        List<SecureHash> result = wireTransaction.getGroupHashes();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHashes);
    }

    @Test
    public void getGroupsMerkleRoots() {
        final Map<Integer, SecureHash> integerSecureHashMap = Map.of(1, secureHash);
        doReturn(integerSecureHashMap).when(wireTransaction).getGroupsMerkleRoots();

        Map<Integer, SecureHash> result = wireTransaction.getGroupsMerkleRoots();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(integerSecureHashMap);
    }

    @Test
    public void getAvailableComponentNonces() {
        doReturn(availableComponent).when(wireTransaction).getAvailableComponentNonces();

        Map<Integer, List<SecureHash>> result = wireTransaction.getAvailableComponentNonces();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(availableComponent);
    }

    @Test
    public void getAvailableComponentHashes() {
        doReturn(availableComponent).when(wireTransaction).getAvailableComponentHashes();

        Map<Integer, List<SecureHash>> result = wireTransaction.getAvailableComponentHashes();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(availableComponent);
    }

    @Test
    public void checkSignature() {
        final DigitalSignatureAndMetadata digitalSignatureAndMetadata =
                new DigitalSignatureAndMetadata(
                        new DigitalSignature.WithKey(publicKey, "test".getBytes()),
                        new DigitalSignatureMetadata(Instant.MIN, new HashMap<>())
                );
        final DigitalSignatureVerificationService digitalSignatureVerificationService = mock(DigitalSignatureVerificationService.class);
        wireTransaction.checkSignature(digitalSignatureVerificationService, digitalSignatureAndMetadata);

        verify(wireTransaction, times(1)).checkSignature(digitalSignatureVerificationService, digitalSignatureAndMetadata);
    }

    @Nested
    public class ComponentGroupJavaApiTest {

        private final List<OpaqueBytes> opaqueBytes = List.of(new OpaqueBytes("test".getBytes()));
        private final ComponentGroup componentGroup = new ComponentGroup(1, opaqueBytes);

        @Test
        public void getGroupIndex() {
            int result = componentGroup.getGroupIndex();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(1);
        }

        @Test
        public void getComponents() {
            List<OpaqueBytes> result = componentGroup.getComponents();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(opaqueBytes);
        }
    }
}
