package net.corda.v5.crypto

import net.corda.v5.crypto.exceptions.MerkleTreeException
import net.corda.v5.crypto.mocks.DigestServiceMock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.stream.IntStream
import kotlin.streams.toList
import kotlin.test.assertFailsWith

class PartialMerkleTreeTests {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var leaves: List<SecureHash>
        private lateinit var merkleTree: MerkleTree

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceMock()
            leaves = "abcdef".map {
                digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
            }
            merkleTree = MerkleTree.getMerkleTree(leaves, DigestAlgorithmName.SHA2_256, digestService)
        }
    }

    @Test
    fun `check full tree`() {
        val h = digestService.hash(UUID.randomUUID().toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        val left = MerkleTree.Node(
            h, MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h)),
            MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h))
        )
        val right = MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h))
        val tree = MerkleTree.Node(h, left, right)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(tree, listOf(h)) }
        PartialMerkleTree.build(right, listOf(h, h)) // Node and two leaves.
        PartialMerkleTree.build(MerkleTree.Leaf(h), listOf(h)) // Just a leaf.
    }

    @Test
    fun `Find leaf index`() {
        // A Merkle tree with 20 leaves.
        val sampleLeaves = IntStream.rangeClosed(0, 19).toList().map {
            digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        }
        val merkleTree = MerkleTree.getMerkleTree(sampleLeaves, DigestAlgorithmName.SHA2_256, digestService)

        // Provided hashes are not in the tree.
        assertFailsWith<MerkleTreeException> {
            PartialMerkleTree.build(
                merkleTree, listOf(
                    digestService.hash("20".toByteArray(), DigestAlgorithmName.SHA2_256)
                )
            )
        }
        // One of the provided hashes is not in the tree.
        assertFailsWith<MerkleTreeException> {
            PartialMerkleTree.build(
                merkleTree, listOf(
                    digestService.hash("20".toByteArray(), DigestAlgorithmName.SHA2_256),
                    digestService.hash("1".toByteArray(), DigestAlgorithmName.SHA2_256),
                    digestService.hash("5".toByteArray(), DigestAlgorithmName.SHA2_256)
                )
            )
        }

        val pmt = PartialMerkleTree.build(
            merkleTree, listOf(
                digestService.hash("1".toByteArray(), DigestAlgorithmName.SHA2_256),
                digestService.hash("5".toByteArray(), DigestAlgorithmName.SHA2_256),
                digestService.hash("0".toByteArray(), DigestAlgorithmName.SHA2_256),
                digestService.hash("19".toByteArray(), DigestAlgorithmName.SHA2_256)
            )
        )
        // First leaf.
        assertEquals(0, pmt.leafIndex(digestService.hash("0".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // Second leaf.
        assertEquals(1, pmt.leafIndex(digestService.hash("1".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // A random leaf.
        assertEquals(5, pmt.leafIndex(digestService.hash("5".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // The last leaf.
        assertEquals(19, pmt.leafIndex(digestService.hash("19".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> {
            pmt.leafIndex(digestService.hash("10".toByteArray(), DigestAlgorithmName.SHA2_256))
        }
        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> {
            pmt.leafIndex(digestService.hash("30".toByteArray(), DigestAlgorithmName.SHA2_256))
        }

        val pmtFirstElementOnly =
            PartialMerkleTree.build(merkleTree, listOf(digestService.hash("0".toByteArray(), DigestAlgorithmName.SHA2_256)))
        assertEquals(0, pmtFirstElementOnly.leafIndex(digestService.hash("0".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> {
            pmtFirstElementOnly.leafIndex(digestService.hash("10".toByteArray(), DigestAlgorithmName.SHA2_256))
        }

        val pmtLastElementOnly =
            PartialMerkleTree.build(merkleTree, listOf(digestService.hash("19".toByteArray(), DigestAlgorithmName.SHA2_256)))
        assertEquals(19, pmtLastElementOnly.leafIndex(digestService.hash("19".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> {
            pmtLastElementOnly.leafIndex(digestService.hash("10".toByteArray(), DigestAlgorithmName.SHA2_256))
        }

        val pmtOneElement = PartialMerkleTree.build(merkleTree, listOf(digestService.hash("5".toByteArray(), DigestAlgorithmName.SHA2_256)))
        assertEquals(5, pmtOneElement.leafIndex(digestService.hash("5".toByteArray(), DigestAlgorithmName.SHA2_256)))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> {
            pmtOneElement.leafIndex(digestService.hash("10".toByteArray(), DigestAlgorithmName.SHA2_256))
        }

        val pmtAllIncluded = PartialMerkleTree.build(merkleTree, sampleLeaves)
        for (i in 0..19) assertEquals(
            i,
            pmtAllIncluded.leafIndex(digestService.hash(i.toString().toByteArray(), DigestAlgorithmName.SHA2_256))
        )

        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> {
            pmtAllIncluded.leafIndex(digestService.hash("30".toByteArray(), DigestAlgorithmName.SHA2_256))
        }
    }

    @Test
    fun `build Partial Merkle Tree, only left nodes branch`() {
        val inclHashes = listOf(leaves[3], leaves[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertTrue(pmt.verify(merkleTree.hash, inclHashes, digestService))
    }

    @Test
    fun `build Partial Merkle Tree, include zero leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, emptyList())
        assertTrue(pmt.verify(merkleTree.hash, emptyList(), digestService))
    }

    @Test
    fun `build Partial Merkle Tree, include all leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, leaves)
        assertTrue(pmt.verify(merkleTree.hash, leaves, digestService))
    }

    @Test
    fun `build Partial Merkle Tree - duplicate leaves failure`() {
        val inclHashes = arrayListOf(leaves[3], leaves[5], leaves[3], leaves[5])
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, inclHashes) }
    }

    @Test
    fun `build Partial Merkle Tree - only duplicate leaves, less included failure`() {
        val leaves = "aaa"
        val hashes = leaves.map {
            digestService.hash(it.toString().toByteArray(), DigestAlgorithmName.SHA2_256)
        }
        val mt = MerkleTree.getMerkleTree(hashes, DigestAlgorithmName.SHA2_256, digestService)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(mt, hashes.subList(0, 1)) }
    }

    @Test
    fun `verify Partial Merkle Tree - too many leaves failure`() {
        val inclHashes = arrayListOf(leaves[3], leaves[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.add(leaves[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes, digestService))
    }

    @Test
    fun `verify Partial Merkle Tree - too little leaves failure`() {
        val inclHashes = arrayListOf(leaves[3], leaves[5], leaves[0])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.remove(leaves[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes, digestService))
    }

    @Test
    fun `verify Partial Merkle Tree - duplicate leaves failure`() {
        val mt = MerkleTree.getMerkleTree(
            leaves.subList(0, 5),
            DigestAlgorithmName.SHA2_256,
            digestService
        ) // Odd number of leaves. Last one is duplicated.
        val inclHashes = arrayListOf(leaves[3], leaves[4])
        val pmt = PartialMerkleTree.build(mt, inclHashes)
        inclHashes.add(leaves[4])
        assertFalse(pmt.verify(mt.hash, inclHashes, digestService))
    }

    @Test
    fun `verify Partial Merkle Tree - different leaves failure`() {
        val inclHashes = arrayListOf(leaves[3], leaves[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertFalse(pmt.verify(merkleTree.hash, listOf(leaves[2], leaves[4]), digestService))
    }

    @Test
    fun `verify Partial Merkle Tree - wrong root`() {
        val inclHashes = listOf(leaves[3], leaves[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        val wrongRoot = digestService.hash(leaves[3].bytes + leaves[5].bytes, DigestAlgorithmName.SHA2_256)
        assertFalse(pmt.verify(wrongRoot, inclHashes, digestService))
    }
}
