package net.corda.v5.application.crypto;

import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.merkle.MerkleTree;
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerkleTreeFactoryJavaApiTest {

    private final MerkleTreeFactory merkleTreeFactory = mock(MerkleTreeFactory.class);

    @Test
    void createTree() {
        final MerkleTree tree = mock(MerkleTree.class);
        when(merkleTreeFactory.createTree(any(), any())).thenReturn(tree);

        final List<byte[]> leaves = new ArrayList<>();
        final MerkleTreeHashDigest digest = mock(MerkleTreeHashDigest.class);
        final MerkleTree result = merkleTreeFactory.createTree(leaves, digest);

        Assertions.assertThat(result).isEqualTo(tree);
    }

    @Test
    void createHashDigest() {
        final DigestAlgorithmName digestAlgorithmName = DigestAlgorithmName.SHA2_256;
        final MerkleTreeHashDigest hashDigest = mock(MerkleTreeHashDigest.class);
        // TODO: figure out what to do about the default third argument to createHashDigest, not supported in Java
        // by default. According to our coding standards doc, we should either replace this with a manual
        // overload. See JIRA CORE-8374.
        when(merkleTreeFactory.createHashDigest(any(), any(), any())).thenReturn(hashDigest);

        final MerkleTreeHashDigest result = merkleTreeFactory.createHashDigest(
                "FakeProvider",
                digestAlgorithmName,
                new HashMap<>());
        Assertions.assertThat(result).isEqualTo(hashDigest);
    }
}
