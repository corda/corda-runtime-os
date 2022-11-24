package net.corda.v5.application.crypto.merkle;

import net.corda.v5.crypto.merkle.MerkleProof;
import net.corda.v5.crypto.merkle.MerkleTree;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerkleTreeJavaApiTest {

    private final MerkleTree merkleTree = mock(MerkleTree.class);

    @Test
    void createAuditProof() {
        final MerkleProof proof = mock(MerkleProof.class);
        when(merkleTree.createAuditProof(any())).thenReturn(proof);

        final List<Integer> leafIndices = new ArrayList<>();
        final MerkleProof result = merkleTree.createAuditProof(leafIndices);

        Assertions.assertThat(result).isEqualTo(proof);
    }
}
