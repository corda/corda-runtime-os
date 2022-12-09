package net.corda.crypto.merkle.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.toByteArray
import net.corda.crypto.merkle.impl.mocks.getZeroHash
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.security.SecureRandom
import kotlin.experimental.xor

class MerkleTreeTest {
    companion object {
        val digestAlgorithm = DigestAlgorithmName.SHA2_256D

        private lateinit var digestService: DigestService
        private lateinit var defaultHashDigestProvider: DefaultHashDigestProvider
        private lateinit var nonceHashDigestProvider: NonceHashDigestProvider
        private lateinit var nonceHashDigestProviderVerify: NonceHashDigestProvider
        private lateinit var secureRandom: SecureRandom


        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService =
                DigestServiceImpl(PlatformDigestServiceImpl(schemeMetadata), null)
            secureRandom = schemeMetadata.secureRandom

            defaultHashDigestProvider = DefaultHashDigestProvider(digestAlgorithm, digestService)
            nonceHashDigestProvider = NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)
            nonceHashDigestProviderVerify = NonceHashDigestProvider.Verify(digestAlgorithm, digestService)
        }

        @JvmStatic
        fun supportedDigestProviders(): List<MerkleTreeHashDigestProvider>{
            return listOf(
                DefaultHashDigestProvider(digestAlgorithm, digestService),
                TweakableHashDigestProvider(digestAlgorithm, digestService, "0".toByteArray(), "1".toByteArray()),
                NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom),
                NonceHashDigestProvider.Verify(digestAlgorithm, digestService),
                NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithm, digestService),
            )
        }

        @JvmStatic
        fun merkleProofTestSizes(): List<Int> = (1 until 16).toList()
    }

    @Test
    fun `Tweakable hash digest provider argument min length tests`() {
        assertDoesNotThrow {
            TweakableHashDigestProvider(digestAlgorithm, digestService, "0".toByteArray(), "1".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java)  {
            TweakableHashDigestProvider(digestAlgorithm, digestService, "".toByteArray(), "1".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java)  {
            TweakableHashDigestProvider(digestAlgorithm, digestService, "0".toByteArray(), "".toByteArray())
        }
    }

    @Test
    fun `next power tests`() {
        assertEquals(1, MerkleTreeImpl.nextHigherPower2(1))
        assertEquals(2, MerkleTreeImpl.nextHigherPower2(2))
        assertEquals(4, MerkleTreeImpl.nextHigherPower2(3))
        assertEquals(0x20000, MerkleTreeImpl.nextHigherPower2(0x12345))
        assertEquals(0x40000000, MerkleTreeImpl.nextHigherPower2(0x30000000))
        assertThrows(IllegalArgumentException::class.java) { MerkleTreeImpl.nextHigherPower2(0) }
        assertThrows(IllegalArgumentException::class.java) { MerkleTreeImpl.nextHigherPower2(-5) }
        assertThrows(IllegalArgumentException::class.java) { MerkleTreeImpl.nextHigherPower2(0x7FFFFFFF) }
    }

    private fun MerkleTreeImpl.calcLeafHash(index: Int): SecureHash {
        return digest.leafHash(
            index,
            digest.leafNonce(index),
            leaves[index]
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when building Merkle tree with empty list of leaves`() {
        assertThrows(IllegalArgumentException::class.java) {
            MerkleTreeImpl.createMerkleTree(emptyList(), defaultHashDigestProvider)
        }
    }

    @Test
    fun `tree test 1 node`() {
        val leafData = (0..0).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        assertEquals(leaf0, root)
    }

    @Test
    fun `tree test 2 node`() {
        val leafData = (0..1).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val manualRoot = merkleTree.digest.nodeHash(0, leaf0, leaf1)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 3 node`() {
        val leafData = (0..2).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val node1 = merkleTree.digest.nodeHash(1, leaf0, leaf1)
        val manualRoot = merkleTree.digest.nodeHash(0, node1, leaf2)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 4 node`() {
        val leafData = (0..3).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val leaf3 = merkleTree.calcLeafHash(3)
        val node1 = merkleTree.digest.nodeHash(1, leaf0, leaf1)
        val node2 = merkleTree.digest.nodeHash(1, leaf2, leaf3)
        val manualRoot = merkleTree.digest.nodeHash(0, node1, node2)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 5 node`() {
        val leafData = (0..4).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val leaf3 = merkleTree.calcLeafHash(3)
        val leaf4 = merkleTree.calcLeafHash(4)
        val node1 = merkleTree.digest.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digest.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digest.nodeHash(1, node1, node2)
        val manualRoot = merkleTree.digest.nodeHash(0, node3, leaf4)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 6 node`() {
        val leafData = (0..5).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val leaf3 = merkleTree.calcLeafHash(3)
        val leaf4 = merkleTree.calcLeafHash(4)
        val leaf5 = merkleTree.calcLeafHash(5)
        val node1 = merkleTree.digest.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digest.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digest.nodeHash(1, node1, node2)
        val node4 = merkleTree.digest.nodeHash(1, leaf4, leaf5)
        val manualRoot = merkleTree.digest.nodeHash(0, node3, node4)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 7 node`() {
        val leafData = (0..6).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val leaf3 = merkleTree.calcLeafHash(3)
        val leaf4 = merkleTree.calcLeafHash(4)
        val leaf5 = merkleTree.calcLeafHash(5)
        val leaf6 = merkleTree.calcLeafHash(6)
        val node1 = merkleTree.digest.nodeHash(3, leaf0, leaf1)
        val node2 = merkleTree.digest.nodeHash(3, leaf2, leaf3)
        val node3 = merkleTree.digest.nodeHash(2, leaf4, leaf5)
        val node4 = merkleTree.digest.nodeHash(2, node1, node2)
        val node5 = merkleTree.digest.nodeHash(1, node3, leaf6)
        val manualRoot = merkleTree.digest.nodeHash(0, node4, node5)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `tree test 8 node`() {
        val leafData = (0..7).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val root = merkleTree.root
        val leaf0 = merkleTree.calcLeafHash(0)
        val leaf1 = merkleTree.calcLeafHash(1)
        val leaf2 = merkleTree.calcLeafHash(2)
        val leaf3 = merkleTree.calcLeafHash(3)
        val leaf4 = merkleTree.calcLeafHash(4)
        val leaf5 = merkleTree.calcLeafHash(5)
        val leaf6 = merkleTree.calcLeafHash(6)
        val leaf7 = merkleTree.calcLeafHash(7)
        val node1 = merkleTree.digest.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digest.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digest.nodeHash(2, leaf4, leaf5)
        val node4 = merkleTree.digest.nodeHash(2, leaf6, leaf7)
        val node5 = merkleTree.digest.nodeHash(1, node1, node2)
        val node6 = merkleTree.digest.nodeHash(1, node3, node4)
        val manualRoot = merkleTree.digest.nodeHash(0, node5, node6)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `Different merkle trees should not be equal`() {
        val leaves1 = "abcdef".map { it.toString().toByteArray() }
        val leaves2 = "ghijkl".map { it.toString().toByteArray() }
        val tree1 = MerkleTreeImpl.createMerkleTree(leaves1, defaultHashDigestProvider)
        val tree2 = MerkleTreeImpl.createMerkleTree(leaves2, defaultHashDigestProvider)
        assertNotEquals(tree1.root, tree2.root)
        assertNotEquals(tree1, tree2)
    }

    @ParameterizedTest(name = "merkle proof tests for trees with {0} leaves")
    @MethodSource("merkleProofTestSizes")
    fun `merkle proofs`(treeSize: Int) {
        val leafData = (0 until treeSize).map { it.toByteArray() }
        val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, nonceHashDigestProvider)

        if (merkleTree.leaves.isNotEmpty()) {
            // Should not build proof for empty list
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(emptyList())
            }

            // Cannot build proof for non-existing index
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(listOf(treeSize + 1))
            }

            // Cannot build proof if any of the indices do not exist in the tree
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(listOf(0, treeSize + 1))
            }

            // Should not create proof if indices have been duplicated
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(listOf(treeSize - 1, treeSize - 1))
            }
        }

        if (merkleTree.leaves.size > 1) {
            // Should not create proof if there are duplicated indices between the others
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(listOf(0, 0, treeSize - 1))
            }
        }

        val root = merkleTree.root

        // Test all the possible combinations of leaves for the proof.
        for (i in 1 until (1 shl treeSize)) {
            val powerSet = (0 until treeSize).filter { (i and (1 shl it)) != 0 }
            val proof = merkleTree.createAuditProof(powerSet)

            // The original root can be reconstructed from the proof
            assertTrue(proof.verify(root, nonceHashDigestProviderVerify))

            // Wrong root should not be accepted.
            val wrongRootBytes = root.bytes
            wrongRootBytes[0] = wrongRootBytes[0] xor 1
            val wrongRootHash = SecureHash(DigestAlgorithmName.SHA2_256D.name, wrongRootBytes)
            assertFalse(proof.verify(wrongRootHash, nonceHashDigestProviderVerify))

            // We break the leaves one by one. All of them should break the proof.
            for (leaf in proof.leaves) {
                val data = leaf.leafData
                data[0] = data[0] xor 1
                assertFalse ( proof.verify(root, nonceHashDigestProviderVerify))
                data[0] = data[0] xor 1
            }

            // We break the hashes one by one. All of them should break the proof.
            for (j in 0 until proof.hashes.size) {
                val badHashes = proof.hashes.toMutableList()
                val badHashBytes = badHashes[j].bytes
                badHashBytes[0] = badHashBytes[0] xor 1
                badHashes[j] = SecureHash(DigestAlgorithmName.SHA2_256D.name, badHashBytes)
                val badProof : MerkleProof =
                    MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves, badHashes)
                assertFalse(badProof.verify(root, nonceHashDigestProviderVerify))
            }

            // We add one extra hash which breaks the proof.
            val badProof1: MerkleProof =
                MerkleProofImpl(
                    MerkleProofType.AUDIT, proof.treeSize, proof.leaves, proof.hashes + digestService.getZeroHash(
                        digestAlgorithm
                    )
                )
            assertFalse( badProof1.verify(root, nonceHashDigestProviderVerify))

            // We remove one hash which breaks the proof.
            if (proof.hashes.size > 1) {
                val badProof2: MerkleProof =
                    MerkleProofImpl(
                        MerkleProofType.AUDIT,
                        proof.treeSize,
                        proof.leaves,
                        proof.hashes.take(proof.hashes.size - 1)
                    )
                assertFalse( badProof2.verify(root, nonceHashDigestProviderVerify))
            }

            // We remove one leaf which breaks the proof.
            if (proof.leaves.size > 1) {
                val badProof3: MerkleProof =
                    MerkleProofImpl(
                        MerkleProofType.AUDIT,
                        proof.treeSize,
                        proof.leaves.take(proof.leaves.size - 1),
                        proof.hashes
                    )
                assertFalse( badProof3.verify(root, nonceHashDigestProviderVerify))
            }

            // If there are leaves not have been added yet
            val notInProofLeaves = (0 until treeSize).filter { (i in powerSet) }
            if (notInProofLeaves.isNotEmpty()) {
                val extraIndex = notInProofLeaves.first()
                val extraLeaf = IndexedMerkleLeaf(extraIndex,
                    nonceHashDigestProvider.leafNonce(extraIndex),
                    merkleTree.leaves[extraIndex]
                )
                // We add one leaf which breaks the proof.
                val badProof4: MerkleProof =
                    MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves + extraLeaf, proof.hashes)
                assertFalse( badProof4.verify(root, nonceHashDigestProviderVerify))

                // We replace one leaf which breaks the proof.
                val badProof5: MerkleProof =
                    MerkleProofImpl(
                        MerkleProofType.AUDIT,
                        proof.treeSize,
                        proof.leaves.dropLast(1) + extraLeaf,
                        proof.hashes
                    )
                assertFalse( badProof5.verify(root, nonceHashDigestProviderVerify))

            }

            // We duplicate one leaf which breaks the proof.
            val badProof6: MerkleProof =
                MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves + proof.leaves.last(), proof.hashes)
            assertFalse( badProof6.verify(root, nonceHashDigestProviderVerify))

        }
    }

    @Test
    fun `test different merkle tree types give different hashes`() {
        val leafData = (0 until 16).map { it.toByteArray() }
        val leafList = listOf(1, 7, 11)
        val merkleTreeDefault = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val proof1 = merkleTreeDefault.createAuditProof(leafList)
        assertEquals(true, proof1.verify(merkleTreeDefault.root, defaultHashDigestProvider))
        val tweakedHash = TweakableHashDigestProvider(digestAlgorithm, digestService, ByteArray(4) { 0x12 }, ByteArray(4) { 0x34 })
        val merkleTreeTweaked = MerkleTreeImpl.createMerkleTree(leafData, tweakedHash)
        val proof2 = merkleTreeTweaked.createAuditProof(leafList)
        assertEquals(true, proof2.verify(merkleTreeTweaked.root, tweakedHash))
        val nonceMerkleTree1 = MerkleTreeImpl.createMerkleTree(
            leafData,
            NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)
        )
        val proof3 = nonceMerkleTree1.createAuditProof(leafList)
        assertEquals(true, proof3.verify(nonceMerkleTree1.root, nonceHashDigestProviderVerify))
        val nonceMerkleTree2 = MerkleTreeImpl.createMerkleTree(
            leafData,
            NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)
        )
        val proof4 = nonceMerkleTree2.createAuditProof(leafList)
        assertEquals(true, proof4.verify(nonceMerkleTree2.root, nonceHashDigestProviderVerify))
        val roots = setOf(merkleTreeDefault.root, merkleTreeTweaked.root, nonceMerkleTree1.root, nonceMerkleTree2.root)
        assertEquals(4, roots.size)
        assertNotEquals(proof3, proof4)
    }

    @Test
    fun `Size only proof for NonceHashDigestProvider`() {
        val leafData = (0 until 18).map { it.toByteArray() }
        val nonceDigest = nonceHashDigestProvider
        val nonceMerkleTree = MerkleTreeImpl.createMerkleTree(leafData, nonceDigest)
        val sizeOnlyProof = nonceDigest.getSizeProof(leafData)
        assertEquals(leafData.size, sizeOnlyProof.leaves.size)
        assertEquals(
            true,
            sizeOnlyProof.verify(nonceMerkleTree.root,
                NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithm, digestService)
            )
        )
    }

    @ParameterizedTest(name = "{0} digest provider should guarantee the same hash used in the whole tree")
    @MethodSource("supportedDigestProviders")
    fun `Digest Providers should guarantee the same hash used in the whole tree`(candidate: MerkleTreeHashDigestProvider) {
        val matching = SecureHash(digestAlgorithm.name, "abc".toByteArray())
        val nonMatching = SecureHash(DigestAlgorithmName.SHA2_256.name, "abc".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            candidate.nodeHash(1, matching, nonMatching)
        }
        assertThrows(IllegalArgumentException::class.java) {
            candidate.nodeHash(1, nonMatching, nonMatching)
        }
    }
}