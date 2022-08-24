package net.corda.v5.ledger.obsolete.merkle;

import net.corda.v5.cipher.suite.DigestService;
import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.mocks.DigestServiceMock;
import net.corda.v5.ledger.obsolete.mocks.DigestServiceMockUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MerkleTreeJavaApiTest {
    private static DigestService digestService;

    @BeforeAll
    public static void setup() {
        digestService = new DigestServiceMock();
    }

    private static SecureHash leaf1Hash(DigestAlgorithmName algorithm) {
        return digestService.hash("leaf1".getBytes(), algorithm);
    }

    private static List<SecureHash> oneLeaf(DigestAlgorithmName algorithm) {
        var list = new ArrayList<SecureHash>();
        list.add(leaf1Hash(algorithm));
        return list;
    }

    @Test
    public void shouldCreateMerkleTree() {
        var merkle = MerkleTree.getMerkleTree(oneLeaf(DigestAlgorithmName.SHA2_256), DigestAlgorithmName.SHA2_256, digestService);
        assertTrue(merkle instanceof MerkleTree.Node);
        assertTrue(((MerkleTree.Node) merkle).getLeft() instanceof MerkleTree.Leaf);
        assertTrue(((MerkleTree.Node) merkle).getRight() instanceof MerkleTree.Leaf);
        assertEquals(((MerkleTree.Node) merkle).getLeft().getHash(), leaf1Hash(DigestAlgorithmName.SHA2_256));
        assertEquals(
            ((MerkleTree.Node) merkle).getRight().getHash(),
            DigestServiceMockUtils.getZeroHash(digestService, DigestAlgorithmName.SHA2_256)
        );
        assertEquals(
            SecureHash.parse("SHA-256:2D1CEDB13469F32862568007BBCE1930A4924E1F68AE0B29A5B0B8056F79061E"),
            merkle.getHash()
        );
    }
}
