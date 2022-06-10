package net.corda.crypto.merkle

import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.experimental.xor
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import net.corda.crypto.core.toByteArray
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.getZeroHash
import net.corda.v5.crypto.merkle.MerkleProof
import org.junit.jupiter.api.BeforeAll


class MerkleTreeTest {
    companion object {
        val digestAlgorithm = DigestAlgorithmName.SHA2_256D

        private lateinit var digestService: DigestService
        private lateinit var defaultHashDigestProvider: DefaultHashDigestProvider
        private lateinit var nonceVerify: NonceHashDigestProvider
        private lateinit var zeroHash: SecureHash

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            defaultHashDigestProvider = DefaultHashDigestProvider(digestAlgorithm, digestService)
            nonceVerify = NonceHashDigestProvider.Verify(digestAlgorithm, digestService)
        }
    }

    @Test
    fun `next power tests`() {
        assertEquals(1, MerkleTreeImpl.nextHigherPower2(1))
        assertEquals(2, MerkleTreeImpl.nextHigherPower2(2))
        assertEquals(4, MerkleTreeImpl.nextHigherPower2(3))
        assertEquals(0x20000, MerkleTreeImpl.nextHigherPower2(0x12345))
        assertEquals(0x40000000, MerkleTreeImpl.nextHigherPower2(0x30000000))
        assertFailsWith<IllegalArgumentException> { MerkleTreeImpl.nextHigherPower2(0) }
        assertFailsWith<IllegalArgumentException> { MerkleTreeImpl.nextHigherPower2(-5) }
        assertFailsWith<IllegalArgumentException> { MerkleTreeImpl.nextHigherPower2(0x7FFFFFFF) }
    }

    private fun MerkleTreeImpl.calcLeafHash(index: Int): SecureHash {
        return digestProvider.leafHash(
            index,
            digestProvider.leafNonce(index),
            leaves[index]
        )
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
        val manualRoot = merkleTree.digestProvider.nodeHash(0, leaf0, leaf1)
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
        val node1 = merkleTree.digestProvider.nodeHash(1, leaf0, leaf1)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node1, leaf2)
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
        val node1 = merkleTree.digestProvider.nodeHash(1, leaf0, leaf1)
        val node2 = merkleTree.digestProvider.nodeHash(1, leaf2, leaf3)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node1, node2)
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
        val node1 = merkleTree.digestProvider.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digestProvider.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digestProvider.nodeHash(1, node1, node2)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node3, leaf4)
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
        val node1 = merkleTree.digestProvider.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digestProvider.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digestProvider.nodeHash(1, node1, node2)
        val node4 = merkleTree.digestProvider.nodeHash(1, leaf4, leaf5)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node3, node4)
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
        val node1 = merkleTree.digestProvider.nodeHash(3, leaf0, leaf1)
        val node2 = merkleTree.digestProvider.nodeHash(3, leaf2, leaf3)
        val node3 = merkleTree.digestProvider.nodeHash(2, leaf4, leaf5)
        val node4 = merkleTree.digestProvider.nodeHash(2, node1, node2)
        val node5 = merkleTree.digestProvider.nodeHash(1, node3, leaf6)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node4, node5)
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
        val node1 = merkleTree.digestProvider.nodeHash(2, leaf0, leaf1)
        val node2 = merkleTree.digestProvider.nodeHash(2, leaf2, leaf3)
        val node3 = merkleTree.digestProvider.nodeHash(2, leaf4, leaf5)
        val node4 = merkleTree.digestProvider.nodeHash(2, leaf6, leaf7)
        val node5 = merkleTree.digestProvider.nodeHash(1, node1, node2)
        val node6 = merkleTree.digestProvider.nodeHash(1, node3, node4)
        val manualRoot = merkleTree.digestProvider.nodeHash(0, node5, node6)
        assertEquals(manualRoot, root)
    }

    @Test
    fun `merkle proofs`() {
        for (treeSize in 1 until 16) {
            val leafData = (0 until treeSize).map { it.toByteArray() }
            val merkleTree = MerkleTreeImpl.createMerkleTree(leafData, NonceHashDigestProvider(digestAlgorithm, digestService))
            val root = merkleTree.root
            for (i in 1 until (1 shl treeSize)) {
                val powerSet = (0 until treeSize).filter { (i and (1 shl it)) != 0 }
                val proof = merkleTree.createAuditProof(powerSet)
                for (leaf in proof.leaves) {
                    val data = leaf.leafData
                    data[0] = data[0] xor 1
                    assertEquals(false, proof.verify(root, nonceVerify))
                    data[0] = data[0] xor 1
                }
                for (j in 0 until proof.hashes.size) { // review CORE-4984
                    val badHashes = proof.hashes.toMutableList()
                    val badHashBytes = badHashes[j].bytes
                    badHashBytes[0] = badHashBytes[0] xor 1
                    badHashes[j] = SecureHash(DigestAlgorithmName.SHA2_256D.name, badHashBytes)
                    val badProof : MerkleProof =
                        MerkleProofImpl(proof.treeSize, proof.leaves, badHashes)
                    assertEquals(false, badProof.verify(root, nonceVerify))
                }
                val badProof1: MerkleProof =
                    MerkleProofImpl(proof.treeSize, proof.leaves, proof.hashes + digestService.getZeroHash(
                        digestAlgorithm))
                assertEquals(false, badProof1.verify(root, nonceVerify))
                if (proof.hashes.size > 1) {
                    val badProof2: MerkleProof =
                        MerkleProofImpl(proof.treeSize, proof.leaves, proof.hashes.take(proof.hashes.size - 1))
                    assertEquals(false, badProof2.verify(root, nonceVerify))
                }
                if (proof.leaves.size > 1) {
                    val badProof3: MerkleProof =
                        MerkleProofImpl(proof.treeSize, proof.leaves.take(proof.leaves.size - 1), proof.hashes)
                    assertEquals(false, badProof3.verify(root, nonceVerify))
                }
            }
        }
    }
/*
    @Test
    fun `nonce digest serialisation test`() {
        val provider1 = NonceHashDigestProvider()
        val serialised = provider1.serialize()
        val deserialised = NonceHashDigestProvider.deserialize(serialised)
        assertEquals(provider1, deserialised)
        assertArrayEquals(provider1.leafNonce(1), deserialised.leafNonce(1))
        assertEquals(false, provider1.leafNonce(0).contentEquals(provider1.leafNonce(1)))
        assertEquals(false, provider1.leafNonce(0).contentEquals(provider1.leafNonce(1)))
    }
*/
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
        val nonceMerkleTree1 = MerkleTreeImpl.createMerkleTree(leafData, NonceHashDigestProvider(digestAlgorithm, digestService))
        val proof3 = nonceMerkleTree1.createAuditProof(leafList)
        assertEquals(true, proof3.verify(nonceMerkleTree1.root, nonceVerify))
        val nonceMerkleTree2 = MerkleTreeImpl.createMerkleTree(leafData, NonceHashDigestProvider(digestAlgorithm, digestService))
        val proof4 = nonceMerkleTree2.createAuditProof(leafList)
        assertEquals(true, proof4.verify(nonceMerkleTree2.root, nonceVerify))
        val roots = setOf(merkleTreeDefault.root, merkleTreeTweaked.root, nonceMerkleTree1.root, nonceMerkleTree2.root)
        assertEquals(4, roots.size)
        assertNotEquals(proof3, proof4)
    }

    @Test
    fun `Size only proof for NonceHashDigestProvider`() {
        val leafData = (0 until 18).map { it.toByteArray() }
        val nonceDigest = NonceHashDigestProvider(digestAlgorithm, digestService)
        val nonceMerkleTree = MerkleTreeImpl.createMerkleTree(leafData, nonceDigest)
        val sizeOnlyProof = nonceDigest.getSizeProof(leafData)
        assertEquals(leafData.size, sizeOnlyProof.leaves.size)
        assertEquals(
            true,
            sizeOnlyProof.verify(nonceMerkleTree.root, NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithm, digestService))
        )
    }
}