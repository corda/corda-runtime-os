package net.corda.crypto.cipher.suite.merkle;

import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider;
import net.corda.v5.crypto.merkle.MerkleTree;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerkleTreeProviderJavaApiTest {

    private final MerkleTreeProvider merkleTreeProvider = mock(MerkleTreeProvider.class);

    @Test
    void createTree() {
        final MerkleTree tree = mock(MerkleTree.class);
        when(merkleTreeProvider.createTree(any(), any())).thenReturn(tree);

        final List<byte[]> leaves = new ArrayList<>();
        final MerkleTreeHashDigestProvider digestProvider = mock(MerkleTreeHashDigestProvider.class);
        final MerkleTree result = merkleTreeProvider.createTree(leaves, digestProvider);

        Assertions.assertThat(result).isEqualTo(tree);
    }

    @Test
    void createHashDigestProvider() {
        final DigestAlgorithmName digestAlgorithmName = DigestAlgorithmName.SHA2_256;
        final MerkleTreeHashDigestProvider hashDigestProvider = mock(MerkleTreeHashDigestProvider.class);
        // TODO: figure out what to do about the default third argument to createHashDigestProvider, not supported in Java
        // by default. According to our coding standards doc, we should either replace this with a manual
        // overload. See JIRA CORE-8374.
        when(merkleTreeProvider.createHashDigestProvider(any(), any(), any())).thenReturn(hashDigestProvider);

        final MerkleTreeHashDigestProvider result = merkleTreeProvider.createHashDigestProvider(
                "FakeProvider",
                digestAlgorithmName,
                new HashMap<>());
        Assertions.assertThat(result).isEqualTo(hashDigestProvider);
    }
}
