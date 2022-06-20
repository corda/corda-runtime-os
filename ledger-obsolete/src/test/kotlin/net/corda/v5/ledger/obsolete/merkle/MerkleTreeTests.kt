package net.corda.v5.ledger.obsolete.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.create
import net.corda.v5.crypto.getZeroHash
import net.corda.v5.ledger.obsolete.mocks.DigestServiceMock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerkleTreeTests {
    companion object {
        private lateinit var digestService: DigestService

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceMock()
        }
    }

    private fun leaf1Hash(algorithm: DigestAlgorithmName): SecureHash = digestService.hash("leaf1".toByteArray(), algorithm)

    private fun leaf2Hash(algorithm: DigestAlgorithmName): SecureHash = digestService.hash("leaf2".toByteArray(), algorithm)

    private fun leaf3Hash(algorithm: DigestAlgorithmName): SecureHash = digestService.hash("leaf3".toByteArray(), algorithm)

    private fun oneLeaf(algorithm: DigestAlgorithmName): List<SecureHash> = listOf(
        leaf1Hash(algorithm)
    )

    private fun twoLeaves(algorithm: DigestAlgorithmName): List<SecureHash> = listOf(
        leaf1Hash(algorithm),
        leaf2Hash(algorithm)
    )

    private fun threeLeaves(algorithm: DigestAlgorithmName): List<SecureHash> = listOf(
        leaf1Hash(algorithm),
        leaf2Hash(algorithm),
        leaf3Hash(algorithm)
    )

    @Test
    fun `Should throw MerkleTreeException when building Merkle tree with empty list of leaves`() {
        assertFailsWith<MerkleTreeException> { MerkleTree.getMerkleTree(emptyList(),
            DigestAlgorithmName.SHA2_256, digestService
        ) }
    }

    @Test
    fun `Similar merkle trees should be equal`() {
        val leaves = "abcdef".map {
            digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        }
        val expected = MerkleTree.getMerkleTree(
            leaves + listOf(
                digestService.getZeroHash(DigestAlgorithmName.SHA2_256),
                digestService.getZeroHash(DigestAlgorithmName.SHA2_256)
            ),
            DigestAlgorithmName.SHA2_256,
            digestService
        )
        val actual = MerkleTree.getMerkleTree(leaves, DigestAlgorithmName.SHA2_256, digestService)
        assertEquals(expected.hash, actual.hash)
        assertEquals(expected, actual)
    }

    @Test
    fun `Different merkle trees should not be equal`() {
        val leaves1 = "abcdef".map {
            digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        }
        val leaves2 = "ghijkl".map {
            digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        }
        val expected = MerkleTree.getMerkleTree(leaves1, DigestAlgorithmName.SHA2_256, digestService)
        val actual = MerkleTree.getMerkleTree(leaves2, DigestAlgorithmName.SHA2_256, digestService)
        assertNotEquals(expected.hash, actual.hash)
        assertNotEquals(expected, actual)
    }

    @Test
    fun `Should create MerkleTree out of one leaf with SHA256 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(oneLeaf(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(merkle.right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_256))
        assertEquals(digestService.create("SHA-256:2D1CEDB13469F32862568007BBCE1930A4924E1F68AE0B29A5B0B8056F79061E"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of one leaf with SHA384 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(oneLeaf(DigestAlgorithmName.SHA2_384),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_384))
        assertEquals(merkle.right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_384))
        assertEquals(digestService.create("SHA-256:5663FAC30716082F371BAD754F174EA7745A88860D998513365699E2674D923C"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of one leaf with SHA256 using SHA384 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(oneLeaf(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_384, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(merkle.right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_256))
        assertEquals(
            digestService.create("SHA-384:16D25072B2671729D36B3E637181EDA825D58E48B3DD3273251F742EE3225A461CC9D5884CA9E471B900259F74AA60B4"),
            merkle.hash
        )
    }

    @Test
    fun `Should create MerkleTree out of two leaves with SHA256 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(twoLeaves(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(merkle.right.hash, leaf2Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(digestService.create("SHA-256:CEA713A5636F023630CDE24D4F4FFD9CD2B5417D60AF79304C6BA7427DFDBEE1"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of two leaves with SHA384 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(twoLeaves(DigestAlgorithmName.SHA2_384),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_384))
        assertEquals(merkle.right.hash, leaf2Hash(DigestAlgorithmName.SHA2_384))
        assertEquals(digestService.create("SHA-256:45E1BF863D04D01A5CDDAE9754CBC7D9B9F9D892437F496EC6B4100C366A3848"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of two leaves with SHA256 using SHA384 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(twoLeaves(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_384, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue(merkle.right is MerkleTree.Leaf)
        assertEquals(merkle.left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(merkle.right.hash, leaf2Hash(DigestAlgorithmName.SHA2_256))
        assertEquals(
            digestService.create("SHA-384:D8735F176D4979F1C397D3F461B48527943C58B9A89E8DDDBC5C9909F65631FBFB6BD01F07A66AD351F22FE7A4E350FD"),
            merkle.hash
        )
    }

    @Test
    fun `Should create MerkleTree out of three leaves with SHA256 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(threeLeaves(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Node)
        assertTrue(merkle.right is MerkleTree.Node)
        assertEquals(digestService.create("SHA-256:CEA713A5636F023630CDE24D4F4FFD9CD2B5417D60AF79304C6BA7427DFDBEE1"), merkle.left.hash)
        assertEquals(digestService.create("SHA-256:D21583D92245DFEDF8117FB1C4A469BA101F5F06E6CFDD80399AC26628D90B26"), merkle.right.hash)
        assertTrue((merkle.left as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue((merkle.left as MerkleTree.Node).right is MerkleTree.Leaf)
        assertEquals((merkle.left as MerkleTree.Node).left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.left as MerkleTree.Node).right.hash, leaf2Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.right as MerkleTree.Node).left.hash, leaf3Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.right as MerkleTree.Node).right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_256))
        assertEquals(digestService.create("SHA-256:7FBDF4A5C78C3225E796DDE965D8DC05B8957496544E65A860B8069C2A0CB4C2"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of three leaves with SHA384 using SHA256 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(threeLeaves(DigestAlgorithmName.SHA2_384),
            DigestAlgorithmName.SHA2_256, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Node)
        assertTrue(merkle.right is MerkleTree.Node)
        assertEquals(digestService.create("SHA-256:45E1BF863D04D01A5CDDAE9754CBC7D9B9F9D892437F496EC6B4100C366A3848"), merkle.left.hash)
        assertEquals(digestService.create("SHA-256:129BF4367C9A51F1A41832618C1FD44B9E66FFC39D0244F10DC4B9B1BD4A831D"), merkle.right.hash)
        assertTrue((merkle.left as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue((merkle.left as MerkleTree.Node).right is MerkleTree.Leaf)
        assertEquals((merkle.left as MerkleTree.Node).left.hash, leaf1Hash(DigestAlgorithmName.SHA2_384))
        assertEquals((merkle.left as MerkleTree.Node).right.hash, leaf2Hash(DigestAlgorithmName.SHA2_384))
        assertEquals((merkle.right as MerkleTree.Node).left.hash, leaf3Hash(DigestAlgorithmName.SHA2_384))
        assertEquals((merkle.right as MerkleTree.Node).right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_384))
        assertEquals(digestService.create("SHA-256:671659139296E432EF4EA2ABB402C5D45AEABC1FCF8E621B8317EF83602B76F3"), merkle.hash)
    }

    @Test
    fun `Should create MerkleTree out of three leaves with SHA256 using SHA384 for nodes`() {
        val merkle = MerkleTree.getMerkleTree(threeLeaves(DigestAlgorithmName.SHA2_256),
            DigestAlgorithmName.SHA2_384, digestService
        )
        assertTrue(merkle is MerkleTree.Node)
        assertTrue((merkle as MerkleTree.Node).left is MerkleTree.Node)
        assertTrue(merkle.right is MerkleTree.Node)
        assertEquals(
            digestService.create("SHA-384:D8735F176D4979F1C397D3F461B48527943C58B9A89E8DDDBC5C9909F65631FBFB6BD01F07A66AD351F22FE7A4E350FD"),
            merkle.left.hash
        )
        assertEquals(
            digestService.create("SHA-384:90E5730F61EC2D68D983E11F6A4A768167D6D6412D4A80B95CE1F5CC9984E8B2EBA3A36C6DF690D1F63CCDF3619B5055"),
            merkle.right.hash
        )
        assertTrue((merkle.left as MerkleTree.Node).left is MerkleTree.Leaf)
        assertTrue((merkle.left as MerkleTree.Node).right is MerkleTree.Leaf)
        assertEquals((merkle.left as MerkleTree.Node).left.hash, leaf1Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.left as MerkleTree.Node).right.hash, leaf2Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.right as MerkleTree.Node).left.hash, leaf3Hash(DigestAlgorithmName.SHA2_256))
        assertEquals((merkle.right as MerkleTree.Node).right.hash, digestService.getZeroHash(DigestAlgorithmName.SHA2_256))
        assertEquals(
            digestService.create("SHA-384:AFA6D7049EF4051202F3A7975DC54CB0085B2928453AFE4E23FBC9496C7A852679866115D360970F43BEC517D492B9F2"),
            merkle.hash
        )
    }
}