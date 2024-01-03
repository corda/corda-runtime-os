package net.corda.crypto.merkle.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.crypto.merkle.impl.mocks.getZeroHash
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTree
import org.assertj.core.api.AbstractStringAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
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
        private val trivialHashDigestProvider = object : MerkleTreeHashDigestProvider {
            private val ZERO_BYTE = ByteArray(1) { 0 }
            private val ONE_BYTE = ByteArray(1) { 1 }
            private fun hash(b: ByteArray): SecureHash {
                var acc = 0
                for (c in b) acc += c.toInt()
                return SecureHashImpl("byteadd", acc.toByteArray())
            }

            override fun getDigestAlgorithmName(): DigestAlgorithmName = DigestAlgorithmName("add")
            override fun leafNonce(index: Int): ByteArray? = null
            override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
                return hash(concatByteArrays(ZERO_BYTE, bytes))
            }

            override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
                return hash(concatByteArrays(ONE_BYTE, left.serialize(), right.serialize()))
            }
        }

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
        fun supportedDigestProviders(): List<MerkleTreeHashDigestProvider> {
            return listOf(
                DefaultHashDigestProvider(digestAlgorithm, digestService),
                TweakableHashDigestProvider(digestAlgorithm, digestService, "0".toByteArray(), "1".toByteArray()),
                NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom),
                NonceHashDigestProvider.Verify(digestAlgorithm, digestService),
                NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithm, digestService),
            )
        }

        @JvmStatic
        fun merkleProofTestSizes(): List<Int> = (1 until 12).toList()

        @JvmStatic
        fun merkleProofExtendedTestSizes(): List<Int> = (13 until 16).toList()
    }

    @Test
    fun `Tweakable hash digest provider argument min length tests`() {
        assertDoesNotThrow {
            TweakableHashDigestProvider(digestAlgorithm, digestService, "0".toByteArray(), "1".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TweakableHashDigestProvider(digestAlgorithm, digestService, "".toByteArray(), "1".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
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
        assertHash(root, "7901af93")
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
        assertTree(
            merkleTree, """
                bab170b1┳7901af93━ 00:00:00:00
                        ┗471864d3━ 00:00:00:01"""
        )

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
        assertTree(
            merkleTree, """
                a9d5543c┳bab170b1┳7901af93━00:00:00:00
                        ┃        ┗471864d3━00:00:00:01
                        ┗66973b1a━66973b1a━00:00:00:02
            """
        )
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
        assertTree(
            merkleTree, """
                  4817d572┳ff3c3992┳bab170b1┳00:00:00:00
                          ┃        ┃        ┗00:00:00:01
                          ┃        ┗517a5de6┳00:00:00:02
                          ┃                 ┗00:00:00:03
                          ┗92e96986┳e0cc7e23┳00:00:00:04
                                   ┃        ┗00:00:00:05
                                   ┗46086473━00:00:00:06
                                        """
        )
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
        assertTree(
            merkleTree, """
              a868a19c┳ff3c3992┳bab170b1┳00:00:00:00
                      ┃        ┃        ┗00:00:00:01
                      ┃        ┗517a5de6┳00:00:00:02
                      ┃                 ┗00:00:00:03
                      ┗ef03791a┳e0cc7e23┳00:00:00:04
                               ┃        ┗00:00:00:05
                               ┗a1a26281┳00:00:00:06
                                        ┗00:00:00:07
                        """
        )
    }

    @Test
    fun `Different merkle trees should not be equal`() {
        val leaves1 = "abcdef".map { it.toString().toByteArray() }
        val leaves2 = "ghijkl".map { it.toString().toByteArray() }
        val tree1 = MerkleTreeImpl.createMerkleTree(leaves1, defaultHashDigestProvider)
        println(tree1)
        val tree2 = MerkleTreeImpl.createMerkleTree(leaves2, defaultHashDigestProvider)
        assertNotEquals(tree1.root, tree2.root)
        assertNotEquals(tree1, tree2)
    }

    @ParameterizedTest(name = "merkle proof tests for trees with sizes that run fast ({0} leaves)")
    @MethodSource("merkleProofTestSizes")
    fun `merkle proofs fast`(treeSize: Int) {
        runMerkleProofTest(treeSize)
    }

    // This test should be run whenever the merkle tree implementation is changed. It is disabled on CI since
    // it can take 30 seconds.
    @Disabled
    @ParameterizedTest(name = "merkle proof tests for trees with extended sizes that run slow ({0} leaves)")
    @MethodSource("merkleProofExtendedTestSizes")
    fun `merkle proofs slow `(treeSize: Int) {
        runMerkleProofTest(treeSize)
    }

    private fun runMerkleProofTest(treeSize: Int) {
        // we don't want to take the time to do an expensive hash so we'll just make a cheap one
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)
        assertThat(merkleTree.leaves).isNotEmpty()

        // Should not build proof for empty list
        // This is a special case check in the impl we don't really need but since it's there
        // let's have test coverage for it.
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

        if (merkleTree.leaves.size > 1) {
            // Should not create proof if there are duplicated indices between the others
            assertThrows(IllegalArgumentException::class.java) {
                merkleTree.createAuditProof(listOf(0, 0, treeSize - 1))
            }
        }

        // Test all the possible combinations of leaves for the proof.
        for (i in 1 until (1 shl treeSize)) {
            val leafIndicesCombination = (0 until treeSize).filter { (i and (1 shl it)) != 0 }
            testLeafCombination(merkleTree, leafIndicesCombination, merkleTree.root, treeSize).also {
                println(it.toString())

                val hashes = calculateLeveledHashes(it, trivialHashDigestProvider)

                println(
                    "Merkle proof for a tree of size $treeSize with ${hashes.size} " +
                            "hashes supplied in the proof where we know $leafIndicesCombination"
                )

                if (i == 1 && treeSize == 1) {
                    assertThat(hashes).hasSize(0)
                    assertThat(it.toString()).isEqualToIgnoringWhitespace("""
                        ━  0 known data
                    """)
                }
                if (i == 1 && treeSize == 2) {
                    assertThat(hashes).hasSize(1)
                    assertHash(hashes[0].hash, "00000001")
                    assertThat(hashes[0].level).isEqualTo(0)
                    assertThat(it.toString()).isEqualToIgnoringWhitespace("""
                        ┳━ 0 known data
                        ┗━ 1 gap
                    """.trimIndent())
                    assertThat(it.illustrate(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
                        """
                            00000630 (calc)┳00000000 (calc)   ━ 0 known data
                                           ┗00000001 (input 0)━ 1 gap"""
                    )
                }

                if (i == 42 && treeSize == 6) {
                    assertThat(hashes).hasSize(3)
                    assertThat(hashes.map { it.hash.hex() }).isEqualTo(
                        arrayListOf("00000000", "00000002", "00000004")
                    )
                    assertThat(hashes.map { it.level }).isEqualTo(arrayListOf(2, 2, 2))
                    assertThat(it.toString()).isEqualToIgnoringWhitespace("""
                        ┳┳┳0 gap
                        ┃┃┗1 known data
                        ┃┗┳2 gap
                        ┃ ┗3 known data
                        ┗━┳4 gap
                          ┗5 known data
                    """.trimIndent())
                }
            }
        }
    }

    private fun testLeafCombination(
        merkleTree: MerkleTree,
        leafIndicesCombination: List<Int>,
        root: SecureHash,
        treeSize: Int
    ): MerkleProofImpl {
        val proofGeneric = merkleTree.createAuditProof(leafIndicesCombination)
        val proof = proofGeneric as MerkleProofImpl

        println("Proof ${proof.toString()}")
        // The original root can be reconstructed from the proof
        assertEquals(proof.calculateRoot(trivialHashDigestProvider), merkleTree.root)
        assertTrue(proof.verify(root, trivialHashDigestProvider))

        // Wrong root should not be accepted.
        val wrongRootBytes = root.bytes
        wrongRootBytes[0] = wrongRootBytes[0] xor 1
        val wrongRootHash = SecureHashImpl(DigestAlgorithmName.SHA2_256D.name, wrongRootBytes)
        assertFalse(proof.verify(wrongRootHash, trivialHashDigestProvider))

        // We break the leaves one by one. All of them should break the proof.
        for (leaf in proof.leaves) {
            val data = leaf.leafData
            data[0] = data[0] xor 1
            assertFalse(proof.verify(root, trivialHashDigestProvider))
            data[0] = data[0] xor 1
        }

        // We break the hashes one by one. All of them should break the proof.
        for (j in 0 until proof.hashes.size) {
            val badHashes = proof.hashes.toMutableList()
            val badHashBytes = badHashes[j].bytes
            badHashBytes[0] = badHashBytes[0] xor 1
            badHashes[j] = SecureHashImpl(DigestAlgorithmName.SHA2_256D.name, badHashBytes)
            val badProof: MerkleProof =
                MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves, badHashes)
            assertFalse(badProof.verify(root, trivialHashDigestProvider))
        }

        // We add one extra hash which breaks the proof.
        val badProof1: MerkleProof =
            MerkleProofImpl(
                MerkleProofType.AUDIT, proof.treeSize, proof.leaves, proof.hashes + digestService.getZeroHash(
                    digestAlgorithm
                )
            )
        assertFalse(badProof1.verify(root, trivialHashDigestProvider))

        // We remove one hash which breaks the proof.
        if (proof.hashes.size > 1) {
            val badProof2: MerkleProof =
                MerkleProofImpl(
                    MerkleProofType.AUDIT,
                    proof.treeSize,
                    proof.leaves,
                    proof.hashes.take(proof.hashes.size - 1)
                )
            assertFalse(badProof2.verify(root, trivialHashDigestProvider))
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
            assertFalse(badProof3.verify(root, trivialHashDigestProvider))
        }

        // If there are leaves not have been added yet
        val notInProofLeaves = (0 until treeSize).filter { it !in leafIndicesCombination }
        if (notInProofLeaves.isNotEmpty()) {
            val extraIndex = notInProofLeaves.first()
            val extraLeaf = IndexedMerkleLeafImpl(
                extraIndex,
                trivialHashDigestProvider.leafNonce(extraIndex),
                merkleTree.leaves[extraIndex]
            )

            // We add one leaf which breaks the proof.
            val badProofExtraLeaf: MerkleProof =
                MerkleProofImpl(proof.proofType, proof.treeSize, proof.leaves + extraLeaf, proof.hashes)
            assertFalse(badProofExtraLeaf.verify(root, trivialHashDigestProvider))

            // We replace one leaf which breaks the proof, since the leaves will not match the hashes.
            val badProofReplacedLeaf: MerkleProof =
                MerkleProofImpl(
                    MerkleProofType.AUDIT,
                    proof.treeSize,
                    proof.leaves.dropLast(1) + extraLeaf,
                    proof.hashes
                )
            assertFalse(badProofReplacedLeaf.verify(root, trivialHashDigestProvider))

        }

        // We duplicate one leaf which breaks the proof.
        val badProofDuplicateLeaf: MerkleProof =
            MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves + proof.leaves.last(), proof.hashes)
        assertFalse(badProofDuplicateLeaf.verify(root, trivialHashDigestProvider))
        return proof
    }

    /**
     * Make a merkle tree for test purposes
     *
     * The leaf data will be successive integers starting at 0.
     *
     * @param treeSize the number of elements
     * @param hashProvider the functions used to hash the tree
     * @return a MerkleTree object
     */
    private fun makeTestMerkleTree(
        treeSize: Int,
        hashProvider: MerkleTreeHashDigestProvider
    ): MerkleTree {
        // 1. make some leaf data, which will just be successive integers starting at zero
        val leafData = (0 until treeSize).map { it.toByteArray() }
        // 2. make the tree from the leaf data
        return MerkleTreeImpl.createMerkleTree(leafData, hashProvider)
    }

    @Test
    fun `test different merkle tree types give different hashes`() {
        val leafData = (0 until 16).map { it.toByteArray() }
        val leafList = listOf(1, 7, 11)
        val merkleTreeDefault = MerkleTreeImpl.createMerkleTree(leafData, defaultHashDigestProvider)
        val proof1 = merkleTreeDefault.createAuditProof(leafList)
        assertEquals(true, proof1.verify(merkleTreeDefault.root, defaultHashDigestProvider))
        val tweakedHash =
            TweakableHashDigestProvider(digestAlgorithm, digestService, ByteArray(4) { 0x12 }, ByteArray(4) { 0x34 })
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
            sizeOnlyProof.verify(
                nonceMerkleTree.root,
                NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithm, digestService)
            )
        )
    }

    @ParameterizedTest(name = "{0} digest provider should guarantee the same hash used in the whole tree")
    @MethodSource("supportedDigestProviders")
    fun `Digest Providers should guarantee the same hash used in the whole tree`(candidate: MerkleTreeHashDigestProvider) {
        val matching = SecureHashImpl(digestAlgorithm.name, "abc".toByteArray())
        val nonMatching = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "abc".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            candidate.nodeHash(1, matching, nonMatching)
        }
        assertThrows(IllegalArgumentException::class.java) {
            candidate.nodeHash(1, nonMatching, nonMatching)
        }
    }
}

fun SecureHash.hex() = bytes.joinToString(separator = "") { "%02x".format(it) }

fun assertHash(hash: SecureHash, valuePrefix: String): AbstractStringAssert<*> =
    assertThat(hash.hex()).startsWith(valuePrefix)


/**
 * Assert that a merkle tree has exact content.
 *
 * @param actual the `MerkleTree` to examine
 * @param expectedRendered the rendered text form; indentation and extra whitespace before and after is ignored
 */
fun assertTree(actual: MerkleTree, expectedRendered: String): AbstractStringAssert<*> =
    assertThat(actual.toString()).isEqualTo(expectedRendered.trimIndent())
