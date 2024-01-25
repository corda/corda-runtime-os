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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import kotlin.experimental.xor
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.assertFailsWith

class MerkleTreeTest {
    companion object {
        val digestAlgorithm = DigestAlgorithmName.SHA2_256D
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS = 30
        private const val MAXIMUM_TREE_SIZE_FOR_EXHAUSTIVE_MERGE_TESTS = 30

        // Since for subset tests there are 2^(2*n) permutations of source leafs we don't want to test too many,
        // so we do a few in a fixed shuffled order. Any new failures have specific tests to guard against regression,
        // in case the platform random number generator changes.
        //
        // This and the following 2 NUMBER_OF_*_TESTS have all been taken up to 20000 tests without error.
        // Beyond that nothing breaks but the memory requirements of the test framework get unmanageable.
        private const val NUMBER_OF_SUBSET_TESTS = 1000

        // Since there are 60129542144 (slightly less than n*2^(2*n)) permutation for list tests when we go up to tree
        // size 16, so again we take a stable random approach.
        private const val NUMBER_OF_MERGE_TESTS = 1000

        // There are n*2^n proof tests for tree size so again use random testing.
        private const val NUMBER_OF_PROOF_TESTS = 1000

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
        fun merkleProofCombinations(): List<Arguments> {
            val rng = Random(0)
            val combinations = MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong() * (1UL shl MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS)
            return iterator {
                while (true) {
                    val t: ULong = rng.nextULong(0UL..combinations)
                    val treeSize = t % MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong()
                    val sourceProofLeafSet = t / MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong()
                    val leafIndicesCombination =
                        (0 until treeSize.toInt()).filter { (sourceProofLeafSet and (1UL shl it)) != 0UL }
                    if (leafIndicesCombination.isNotEmpty())
                        yield(Arguments.of(treeSize.toInt(), leafIndicesCombination))
                }
            }.asSequence().take(NUMBER_OF_PROOF_TESTS)
                .toList() // Sadly, we don't know how to supply JUnit test parameters from lazy sequences.
        }

        @JvmStatic
        fun merkleProofMergeCombinations(): List<Arguments> {
            // We want to pick tests at random without realizing the entire list first, since
            // it will be too big to hold in memory.
            val rng = Random(0)
            val numberOfTreeSizes = MAXIMUM_TREE_SIZE_FOR_EXHAUSTIVE_MERGE_TESTS - 2
            val combinations: ULong = (
                numberOfTreeSizes.toULong()
                    * (1UL shl MAXIMUM_TREE_SIZE_FOR_EXHAUSTIVE_MERGE_TESTS)
                    * (1UL shl MAXIMUM_TREE_SIZE_FOR_EXHAUSTIVE_MERGE_TESTS)
                )
            return iterator {
                while (true) {
                    val t: ULong = rng.nextULong(0UL..combinations)
                    val treeSize: UInt = 2U + (((t % numberOfTreeSizes.toUInt())).toUInt())
                    val t2: ULong = (t / MAXIMUM_TREE_SIZE_FOR_EXHAUSTIVE_MERGE_TESTS.toULong())
                    val numberOfProofLeafSets: UInt = 1U shl (treeSize.toInt())
                    val sourceProofLeafSet: UInt = (t2 % numberOfProofLeafSets).toUInt()
                    val leafIndicesCombination: List<Int> =
                        (0 until treeSize.toInt()).filter { ((sourceProofLeafSet.toULong()) and (1UL shl (it))) != 0UL }
                    val numberOfSplits: UInt = (1U shl (leafIndicesCombination.size - 1))
                    val t3: ULong = t2 / numberOfProofLeafSets
                    val splitIndex = t3 % numberOfSplits.toULong()
                    val xSet =
                        leafIndicesCombination.filter { leafIndex -> (splitIndex and (1UL shl (leafIndex))) != 0UL }
                    val ySet = leafIndicesCombination.filter { leafIndex -> leafIndex !in xSet }
                    if (numberOfSplits > 0U && xSet.isNotEmpty() && ySet.isNotEmpty()) {
                        yield(Arguments.of(treeSize.toInt(), xSet, ySet))
                    }
                }
            }.asSequence().take(NUMBER_OF_MERGE_TESTS).toList()
        }

        @JvmStatic
        fun subsetTestCombinations(): List<Arguments> {
            val rng = Random(0)
            val combinations: ULong = (
                MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong()
                    * (1UL shl MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS)
                    * (1UL shl MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS))

            return iterator {
                while (true) {
                    val t: ULong = rng.nextULong(0UL..combinations)
                    val treeSize = (t % MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong()).toInt()
                    val t2: ULong = t / MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS.toULong()
                    val sourceProofLeafSet = t2 % (1UL shl MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS)
                    val subsetProofLeafSet = t2 / (1UL shl MAXIMUM_TREE_SIZE_FOR_PROOF_TESTS)

                    val leafIndicesCombination = (0 until treeSize).filter {
                        (sourceProofLeafSet and (1UL shl it)) != 0UL
                    }
                    val subsetLeafIndicesCombination =
                        (0 until treeSize).filter { leaf -> (subsetProofLeafSet and (1UL shl leaf)) != 0UL }
                    if (leafIndicesCombination.isNotEmpty() && subsetLeafIndicesCombination.isNotEmpty())
                    yield(Arguments.of(treeSize, leafIndicesCombination, subsetLeafIndicesCombination))
                }
            }.asSequence().take(NUMBER_OF_SUBSET_TESTS).toList()
        }
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
                bab170b1┳7901af93 00:00:00:00
                        ┗471864d3 00:00:00:01"""
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
                a9d5543c┳bab170b1┳7901af93 00:00:00:00
                        ┃        ┗471864d3 00:00:00:01
                        ┗66973b1a━66973b1a 00:00:00:02
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
              4817d572┳ff3c3992┳bab170b1┳7901af93 00:00:00:00
                      ┃        ┃        ┗471864d3 00:00:00:01
                      ┃        ┗517a5de6┳66973b1a 00:00:00:02
                      ┃                 ┗568f8d2a 00:00:00:03
                      ┗92e96986┳e0cc7e23┳1d3a2328 00:00:00:04
                               ┃        ┗cf5f6713 00:00:00:05
                               ┗46086473━46086473 00:00:00:06
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
                  a868a19c┳ff3c3992┳bab170b1┳7901af93 00:00:00:00
                          ┃        ┃        ┗471864d3 00:00:00:01
                          ┃        ┗517a5de6┳66973b1a 00:00:00:02
                          ┃                 ┗568f8d2a 00:00:00:03
                          ┗ef03791a┳e0cc7e23┳1d3a2328 00:00:00:04
                                   ┃        ┗cf5f6713 00:00:00:05
                                   ┗a1a26281┳46086473 00:00:00:06
                                            ┗b0d020da 00:00:00:07
        """
        )
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

    @ParameterizedTest(name = "tree size {0} leaves, leaf set {1})")
    @MethodSource("merkleProofCombinations")
    fun `test merkle proof`(treeSize: Int, sourceProofLeafSet: List<Int>) {
        makeMerkleProof(treeSize, sourceProofLeafSet)
    }

    private fun makeMerkleProof(
        treeSize: Int,
        sourceProofLeafSet: List<Int>
    ): MerkleProofImpl {
        // we don't want to take the time to do an expensive hash so we'll just make a cheap one.
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
        return makeProof(merkleTree, sourceProofLeafSet)
    }

    @Test
    fun `Leveled hashes for tree size 2 known leaf 0`() {
        val proof = makeMerkleProof(2, listOf(0))
        val hashes = calculateLeveledHashes(proof, trivialHashDigestProvider)
        assertThat(hashes).hasSize(1)
        assertHash(hashes[0].hash, "00000001")
        assertThat(hashes[0].level).isEqualTo(0)
    }

    @Test
    fun `Leveled hashes for tree size 3 known leaf 0`() {
        val proof = makeMerkleProof(3, listOf(0))
        val hashes = calculateLeveledHashes(proof, trivialHashDigestProvider)
        assertThat(hashes).hasSize(2)
    }

    @Test
    fun `Leveled hashes for tree size 6 known leaves 1,3,5`() {
        val proof = makeMerkleProof(6, listOf(1, 3, 5))
        val hashes = calculateLeveledHashes(proof, trivialHashDigestProvider)
        assertThat(hashes).hasSize(3)
        assertThat(hashes.map { it.hash.hex() }).isEqualTo(
            arrayListOf("00000000", "00000002", "00000004")
        )
        assertThat(hashes.map { it.level }).isEqualTo(arrayListOf(2, 2, 2))
    }

    @Test
    fun `Subset leaf 1 for tree size 3 known leaves 1,2`() {
        val proof = makeMerkleProof(3, listOf(1, 2))
        proof.subset(trivialHashDigestProvider, listOf(1))
    }

    @Test
    fun `Subset leaves 1,2 for tree size 3 known leaves 1,2`() {
        val proof = makeMerkleProof(3, listOf(1, 2))
        // a case where all the leaves are defined, and we should not include the leaves in the output
        val subproof = proof.subset(trivialHashDigestProvider, listOf(1, 2))
        val text = subproof.render(trivialHashDigestProvider)
        assertThat(text).isEqualToIgnoringWhitespace("""
            00000667 (calc)┳00000630 (calc)┳00000000 (input 0) filtered
                           ┃               ┗00000001 (calc)    known leaf
                           ┗00000002 (calc)━00000002 (calc)    known leaf
        """)
    }

    @Test
    fun `Subset leaves 0,2 for tree size 4 known leaves 0,2,3`() {
        val proof = makeMerkleProof(4, listOf(0, 2, 3))
        val subproof = proof.subset(trivialHashDigestProvider, listOf(0, 2))
        val text = subproof.render(trivialHashDigestProvider)

        assertThat(text).isEqualToIgnoringWhitespace("""
            0000069F (calc)┳00000630 (calc)┳00000000 (calc)    known leaf
                           ┃               ┗00000001 (input 0) filtered
                           ┗00000634 (calc)┳00000002 (calc)    known leaf
                                           ┗00000003 (input 1) filtered""")
    }

    @ParameterizedTest(name = "tree size {0}, merge {1} with {2}}")
    @MethodSource("merkleProofMergeCombinations")
    fun `test merge`(treeSize: Int, xSubset: List<Int>, ySubset: List<Int>) {
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)
        val leafIndicesCombination = xSubset + ySubset
        val proof = makeProof(merkleTree, leafIndicesCombination)
        val digest = trivialHashDigestProvider
        val proofText = proof.render(digest)
        check(xSubset.size + ySubset.size == leafIndicesCombination.size)
        val xProof = proof.subset(digest, xSubset)
        val yProof = proof.subset(digest, ySubset)
        val mergedProof = xProof.merge(yProof, digest) as MerkleProofImpl
        val mergedProofText = mergedProof.render(digest)
        assertThat(mergedProofText).isEqualTo(proofText)
    }

    @Test
    fun `merkle proof render`() {
        // a single simple test case which is easier to debug than the parameterised tree tests
        val treeSize = 6
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)

        makeProof(merkleTree, listOf(2, 4)).also {
            assertThat(it.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
                """
                00000612 (calc)┳0000069F (calc)┳00000630 (input 2)┳unknown            filtered
                               ┃               ┃                  ┗unknown            filtered
                               ┃               ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                               ┃                                  ┗00000003 (input 0) filtered
                               ┗00000638 (calc)━00000638 (calc)   ┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 1) filtered
                """
            )
        }
    }

    @Test
    fun `merkle proof size 4 merge bug`() {
        val merkleTree = makeTestMerkleTree(4, trivialHashDigestProvider)
        val proof = makeProof(merkleTree, listOf(0, 2, 3))
        val proofText = proof.render(trivialHashDigestProvider)
        assertThat(proofText).isEqualToIgnoringWhitespace(
            """
            0000069F (calc)┳00000630 (calc)┳00000000 (calc)    known leaf
                           ┃               ┗00000001 (input 0) filtered
                           ┗00000634 (calc)┳00000002 (calc)    known leaf
                                           ┗00000003 (calc)    known leaf
        """.trimIndent()
        )
        val xProof = proof.subset(trivialHashDigestProvider, listOf(2))
        assertThat(xProof.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            0000069F (calc)┳00000630 (input 1)┳unknown            filtered
                           ┃                  ┗unknown            filtered
                           ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                                              ┗00000003 (input 0) filtered            
        """.trimIndent()
        )
        val yProof = proof.subset(trivialHashDigestProvider, listOf(0, 3))
        assertThat(yProof.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            0000069F (calc)┳00000630 (calc)┳00000000 (calc)    known leaf
                           ┃               ┗00000001 (input 0) filtered
                           ┗00000634 (calc)┳00000002 (input 1) filtered
                                           ┗00000003 (calc)    known leaf
        """.trimIndent()
        )
        val mergedProof = xProof.merge(yProof, trivialHashDigestProvider) as MerkleProofImpl
        val mergedProofText = mergedProof.render(trivialHashDigestProvider)
        assertThat(mergedProofText).isEqualTo(proofText)
    }

    @Test
    fun `subset size 4 leaves 0,2,3 to 0,3 bug`() {
        val merkleTree = makeTestMerkleTree(4, defaultHashDigestProvider)
        val proof = makeProof(merkleTree, listOf(0, 2, 3))
        val proofText = proof.render(defaultHashDigestProvider)
        assertThat(proofText).isEqualTo(
            """
              FF3C3992 (calc)┳BAB170B1 (calc)┳7901AF93 (calc)    known leaf
                             ┃               ┗471864D3 (input 0) filtered
                             ┗517A5DE6 (calc)┳66973B1A (calc)    known leaf
                                             ┗568F8D2A (calc)    known leaf
        """.trimIndent()
        )
        val subset = proof.subset(defaultHashDigestProvider, listOf(0, 3))
        val subsetText = subset.render(defaultHashDigestProvider)
        assertThat(subsetText).isEqualToIgnoringWhitespace(
            """
            FF3C3992 (calc)┳BAB170B1 (calc)┳7901AF93 (calc)    known leaf
                           ┃               ┗471864D3 (input 0) filtered
                           ┗517A5DE6 (calc)┳66973B1A (input 1) filtered
                                           ┗568F8D2A (calc)    known leaf
                                       """.trimIndent()
        )
    }


    @Test
    fun `merkle proof subset bug`() {
        val treeSize = 6
        val merkleTree = makeTestMerkleTree(treeSize, defaultHashDigestProvider)

        val proof1 = makeProof(merkleTree, listOf(0, 2))
        val proof2 = makeProof(merkleTree, listOf(2))
        assertThat(proof2.render(defaultHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            8696C1F4 (calc)┳FF3C3992 (calc)   ┳BAB170B1 (input 1)┳unknown            filtered
                           ┃                  ┃                  ┗unknown            filtered
                           ┃                  ┗517A5DE6 (calc)   ┳66973B1A (calc)    known leaf
                           ┃                                     ┗568F8D2A (input 0) filtered
                           ┗E0CC7E23 (input 2)━unknown           ┳unknown            filtered
                                                                 ┗unknown            filtered
        """.trimIndent()
        )

        val proofY = proof1.subset(defaultHashDigestProvider, listOf(2))
        assertThat(proofY.render(defaultHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            8696C1F4 (calc)┳FF3C3992 (calc)   ┳BAB170B1 (input 1)┳unknown            filtered
                           ┃                  ┃                  ┗unknown            filtered
                           ┃                  ┗517A5DE6 (calc)   ┳66973B1A (calc)    known leaf
                           ┃                                     ┗568F8D2A (input 0) filtered
                           ┗E0CC7E23 (input 2)━unknown           ┳unknown            filtered
                                                                 ┗unknown            filtered
         """.trimIndent()
        )
    }

    @Test
    fun `merkle proof merge size 6`() {
        val treeSize = 6
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)

        val proof1 = makeProof(merkleTree, listOf(2, 4)).also {
            assertThat(it.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
                """
                00000612 (calc)┳0000069F (calc)┳00000630 (input 2)┳unknown            filtered
                               ┃               ┃                  ┗unknown            filtered
                               ┃               ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                               ┃                                  ┗00000003 (input 0) filtered
                               ┗00000638 (calc)━00000638 (calc)   ┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 1) filtered
                """
            )
        }
        val proof2 = proof1.subset(trivialHashDigestProvider, listOf(2))
        assertThat(proof2.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000612 (calc)┳0000069F (calc)   ┳00000630 (input 1)┳unknown            filtered
                           ┃                  ┃                  ┗unknown            filtered
                           ┃                  ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                           ┃                                     ┗00000003 (input 0) filtered
                           ┗00000638 (input 2)━unknown           ┳unknown            filtered
                                                                 ┗unknown            filtered         
        """.trimIndent()
        )
        val proof3 = proof1.subset(trivialHashDigestProvider, listOf(4))
        assertThat(proof3.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                00000612 (calc)┳0000069F (input 1)┳unknown        ┳unknown            filtered
                               ┃                  ┃               ┗unknown            filtered
                               ┃                  ┗unknown        ┳unknown            filtered
                               ┃                                  ┗unknown            filtered
                               ┗00000638 (calc)   ━00000638 (calc)┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 0) filtered      
        """.trimIndent()
        )
        val proofMerged = proof2.merge(proof3, trivialHashDigestProvider) as MerkleProofImpl
        assertThat(proofMerged.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                00000612 (calc)┳0000069F (calc)┳00000630 (input 2)┳unknown            filtered
                               ┃               ┃                  ┗unknown            filtered
                               ┃               ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                               ┃                                  ┗00000003 (input 0) filtered
                               ┗00000638 (calc)━00000638 (calc)   ┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 1) filtered
        """.trimIndent()
        )
    }

    @Test
    fun `merkle proof merge size 6 0 left 2 right`() {
        val treeSize = 6
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)

        val proof1 = makeProof(merkleTree, listOf(0, 2))
        val proofX = proof1.subset(trivialHashDigestProvider, listOf(0))
        assertThat(proofX.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000612 (calc)┳0000069F (calc)   ┳00000630 (calc)   ┳00000000 (calc)    known leaf
                           ┃                  ┃                  ┗00000001 (input 0) filtered
                           ┃                  ┗00000634 (input 1)┳unknown            filtered
                           ┃                                     ┗unknown            filtered
                           ┗00000638 (input 2)━unknown           ┳unknown            filtered
                                                                 ┗unknown            filtered   
        """.trimIndent()
        )

        val proof1Text = proof1.render(trivialHashDigestProvider)
        assertThat(proof1.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000612 (calc)┳0000069F (calc)   ┳00000630 (calc)┳00000000 (calc)    known leaf
                           ┃                  ┃               ┗00000001 (input 0) filtered
                           ┃                  ┗00000634 (calc)┳00000002 (calc)    known leaf
                           ┃                                  ┗00000003 (input 1) filtered
                           ┗00000638 (input 2)━unknown        ┳unknown            filtered
                                                              ┗unknown            filtered
        """
        )
        val proofY = proof1.subset(trivialHashDigestProvider, listOf(2))
        assertThat(proofY.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                00000612 (calc)┳0000069F (calc)   ┳00000630 (input 1)┳unknown            filtered
                               ┃                  ┃                  ┗unknown            filtered
                               ┃                  ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                               ┃                                     ┗00000003 (input 0) filtered
                               ┗00000638 (input 2)━unknown           ┳unknown            filtered
                                                                     ┗unknown            filtered
        """.trimIndent()
        )
        val proofMerged = proofX.merge(proofY, trivialHashDigestProvider) as MerkleProofImpl
        val proofMergedText = proofMerged.render(trivialHashDigestProvider)
        assertThat(proofMergedText).isEqualTo(proof1Text)
    }

    @Test
    fun `merkle proof merge size 26 left 6 right 5,7,9,10,12,17,21 bug`() {
        val treeSize = 26
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)

        val proof1 = makeProof(merkleTree, listOf(5,6,7,9,10,12,17,21))
        val proofX = proof1.subset(trivialHashDigestProvider, listOf(6))
        assertThat(proofX.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace("""
                00000569 (calc)┳0000058B (calc)   ┳00000589 (calc)   ┳0000069F (input 2)┳unknown           ┳unknown            filtered
                               ┃                  ┃                  ┃                  ┃                  ┗unknown            filtered
                               ┃                  ┃                  ┃                  ┗unknown           ┳unknown            filtered
                               ┃                  ┃                  ┃                                     ┗unknown            filtered
                               ┃                  ┃                  ┗000006AF (calc)   ┳00000638 (input 1)┳unknown            filtered
                               ┃                  ┃                                     ┃                  ┗unknown            filtered
                               ┃                  ┃                                     ┗0000063C (calc)   ┳00000006 (calc)    known leaf
                               ┃                  ┃                                                        ┗00000007 (input 0) filtered
                               ┃                  ┗000005C9 (input 3)┳unknown           ┳unknown           ┳unknown            filtered
                               ┃                                     ┃                  ┃                  ┗unknown            filtered
                               ┃                                     ┃                  ┗unknown           ┳unknown            filtered
                               ┃                                     ┃                                     ┗unknown            filtered
                               ┃                                     ┗unknown           ┳unknown           ┳unknown            filtered
                               ┃                                                        ┃                  ┗unknown            filtered
                               ┃                                                        ┗unknown           ┳unknown            filtered
                               ┃                                                                           ┗unknown            filtered
                               ┗000006A4 (input 4)┳unknown           ┳unknown           ┳unknown           ┳unknown            filtered
                                                  ┃                  ┃                  ┃                  ┗unknown            filtered
                                                  ┃                  ┃                  ┗unknown           ┳unknown            filtered
                                                  ┃                  ┃                                     ┗unknown            filtered
                                                  ┃                  ┗unknown           ┳unknown           ┳unknown            filtered
                                                  ┃                                     ┃                  ┗unknown            filtered
                                                  ┃                                     ┗unknown           ┳unknown            filtered
                                                  ┃                                                        ┗unknown            filtered
                                                  ┗unknown           ━unknown           ━unknown           ┳unknown            filtered
                                                                                                           ┗unknown            filtered
                                                                                                   """.trimIndent()
        )

        val proof1Text = proof1.render(trivialHashDigestProvider)
        assertThat(proof1.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace("""
                00000569 (calc)┳0000058B (calc)┳00000589 (calc)    ┳0000069F (input 9)┳unknown           ┳unknown            filtered
                               ┃               ┃                   ┃                  ┃                  ┗unknown            filtered
                               ┃               ┃                   ┃                  ┗unknown           ┳unknown            filtered
                               ┃               ┃                   ┃                                     ┗unknown            filtered
                               ┃               ┃                   ┗000006AF (calc)   ┳00000638 (calc)   ┳00000004 (input 0) filtered
                               ┃               ┃                                      ┃                  ┗00000005 (calc)    known leaf
                               ┃               ┃                                      ┗0000063C (calc)   ┳00000006 (calc)    known leaf
                               ┃               ┃                                                         ┗00000007 (calc)    known leaf
                               ┃               ┗000005C9 (calc)    ┳000006BF (calc)   ┳00000640 (calc)   ┳00000008 (input 1) filtered
                               ┃                                   ┃                  ┃                  ┗00000009 (calc)    known leaf
                               ┃                                   ┃                  ┗00000644 (calc)   ┳0000000A (calc)    known leaf
                               ┃                                   ┃                                     ┗0000000B (input 2) filtered
                               ┃                                   ┗000006CF (calc)   ┳00000648 (calc)   ┳0000000C (calc)    known leaf
                               ┃                                                      ┃                  ┗0000000D (input 3) filtered
                               ┃                                                      ┗0000064C (input 6)┳unknown            filtered
                               ┃                                                                         ┗unknown            filtered
                               ┗000006A4 (calc)┳00000609 (calc)    ┳000006DF (calc)   ┳00000650 (calc)   ┳00000010 (input 4) filtered
                                               ┃                   ┃                  ┃                  ┗00000011 (calc)    known leaf
                                               ┃                   ┃                  ┗00000654 (input 7)┳unknown            filtered
                                               ┃                   ┃                                     ┗unknown            filtered
                                               ┃                   ┗000006EF (calc)   ┳00000658 (calc)   ┳00000014 (input 5) filtered
                                               ┃                                      ┃                  ┗00000015 (calc)    known leaf
                                               ┃                                      ┗0000065C (input 8)┳unknown            filtered
                                               ┃                                                         ┗unknown            filtered
                                               ┗00000660 (input 10)━unknown           ━unknown           ┳unknown            filtered
                                                                                                         ┗unknown            filtered
        """)
        val proofY = proof1.subset(trivialHashDigestProvider, listOf(5,7,9,10,12,17,21))
        assertThat(proofY.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace("""
                00000569 (calc)┳0000058B (calc)┳00000589 (calc)    ┳0000069F (input 10)┳unknown           ┳unknown            filtered
                               ┃               ┃                   ┃                   ┃                  ┗unknown            filtered
                               ┃               ┃                   ┃                   ┗unknown           ┳unknown            filtered
                               ┃               ┃                   ┃                                      ┗unknown            filtered
                               ┃               ┃                   ┗000006AF (calc)    ┳00000638 (calc)   ┳00000004 (input 0) filtered
                               ┃               ┃                                       ┃                  ┗00000005 (calc)    known leaf
                               ┃               ┃                                       ┗0000063C (calc)   ┳00000006 (input 1) filtered
                               ┃               ┃                                                          ┗00000007 (calc)    known leaf
                               ┃               ┗000005C9 (calc)    ┳000006BF (calc)    ┳00000640 (calc)   ┳00000008 (input 2) filtered
                               ┃                                   ┃                   ┃                  ┗00000009 (calc)    known leaf
                               ┃                                   ┃                   ┗00000644 (calc)   ┳0000000A (calc)    known leaf
                               ┃                                   ┃                                      ┗0000000B (input 3) filtered
                               ┃                                   ┗000006CF (calc)    ┳00000648 (calc)   ┳0000000C (calc)    known leaf
                               ┃                                                       ┃                  ┗0000000D (input 4) filtered
                               ┃                                                       ┗0000064C (input 7)┳unknown            filtered
                               ┃                                                                          ┗unknown            filtered
                               ┗000006A4 (calc)┳00000609 (calc)    ┳000006DF (calc)    ┳00000650 (calc)   ┳00000010 (input 5) filtered
                                               ┃                   ┃                   ┃                  ┗00000011 (calc)    known leaf
                                               ┃                   ┃                   ┗00000654 (input 8)┳unknown            filtered
                                               ┃                   ┃                                      ┗unknown            filtered
                                               ┃                   ┗000006EF (calc)    ┳00000658 (calc)   ┳00000014 (input 6) filtered
                                               ┃                                       ┃                  ┗00000015 (calc)    known leaf
                                               ┃                                       ┗0000065C (input 9)┳unknown            filtered
                                               ┃                                                          ┗unknown            filtered
                                               ┗00000660 (input 11)━unknown            ━unknown           ┳unknown            filtered
                                                                                                          ┗unknown            filtered
        """.trimIndent()
        )
        val proofMerged = proofX.merge(proofY, trivialHashDigestProvider) as MerkleProofImpl
        val proofMergedText = proofMerged.render(trivialHashDigestProvider)
        assertThat(proofMergedText).isEqualTo(proof1Text)
    }

    @Test
    fun `merkle proof merge size 4 0,2,3 left 0 right 2,3`() {
        val treeSize = 4
        val digest = defaultHashDigestProvider
        val merkleTree = makeTestMerkleTree(treeSize, digest)

        val proof1 = makeProof(merkleTree, listOf(0, 2, 3))
        val proof1Text = proof1.render(digest)
        assertThat(proof1.render(digest)).isEqualToIgnoringWhitespace(
            """
            FF3C3992 (calc)┳BAB170B1 (calc)┳7901AF93 (calc)    known leaf
                           ┃               ┗471864D3 (input 0) filtered
                           ┗517A5DE6 (calc)┳66973B1A (calc)    known leaf
                                           ┗568F8D2A (calc)    known leaf
        """
        )
        val proofX = proof1.subset(digest, listOf(0))
        assertThat(proofX.render(digest)).isEqualToIgnoringWhitespace(
            """
            FF3C3992 (calc)┳BAB170B1 (calc)   ┳7901AF93 (calc)    known leaf
                           ┃                  ┗471864D3 (input 0) filtered
                           ┗517A5DE6 (input 1)┳unknown            filtered
                                              ┗unknown            filtered
        """.trimIndent()
        )
        val proofY = proof1.subset(digest, listOf(2, 3))
        assertThat(proofY.render(digest)).isEqualToIgnoringWhitespace(
            """
            FF3C3992 (calc)┳BAB170B1 (input 0)┳unknown         filtered
                           ┃                  ┗unknown         filtered
                           ┗517A5DE6 (calc)   ┳66973B1A (calc) known leaf
                                              ┗568F8D2A (calc) known leaf
        """.trimIndent()
        )
        val proofMerged = proofX.merge(proofY, digest) as MerkleProofImpl
        val proofMergedText = proofMerged.render(digest)
        assertThat(proofMergedText).isEqualTo(proof1Text)
    }

    @Test
    fun `merkle proof size 6 subset A`() {
        // a single simple test case which is easier to debug than the parameterised tree tests
        val treeSize = 6
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)

        val proof = makeProof(merkleTree, listOf(2, 4))
        assertThat(proof.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                    00000612 (calc)┳0000069F (calc)┳00000630 (input 2)┳unknown            filtered
                                   ┃               ┃                  ┗unknown            filtered
                                   ┃               ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                                   ┃                                  ┗00000003 (input 0) filtered
                                   ┗00000638 (calc)━00000638 (calc)   ┳00000004 (calc)    known leaf
                                                                      ┗00000005 (input 1) filtered
        """
        )

        val proof2 = proof.subset(trivialHashDigestProvider, listOf(4))
        assertThat(proof2.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                00000612 (calc)┳0000069F (input 1)┳unknown        ┳unknown            filtered
                               ┃                  ┃               ┗unknown            filtered
                               ┃                  ┗unknown        ┳unknown            filtered
                               ┃                                  ┗unknown            filtered
                               ┗00000638 (calc)   ━00000638 (calc)┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 0) filtered
        """
        )

        val proof3 = proof.subset(trivialHashDigestProvider, listOf(2))
        assertThat(proof3.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
                00000612 (calc)┳0000069F (calc)   ┳00000630 (input 1)┳unknown            filtered
                               ┃                  ┃                  ┗unknown            filtered
                               ┃                  ┗00000634 (calc)   ┳00000002 (calc)    known leaf
                               ┃                                     ┗00000003 (input 0) filtered
                               ┗00000638 (input 2)━unknown           ┳unknown            filtered
                                                                     ┗unknown            filtered
        """
        )
        val proofHash = proof.calculateRoot(trivialHashDigestProvider)
        val proof2Hash = proof2.calculateRoot(trivialHashDigestProvider)
        val proof3Hash = proof3.calculateRoot(trivialHashDigestProvider)
        assertThat(proof2Hash).isEqualTo(proofHash)
        assertThat(proof3Hash).isEqualTo(proofHash)
    }

    @Test
    fun `merkle proof size 3 subsets`() {
        val treeSize = 3
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)
        val proof = makeProof(merkleTree, listOf(0, 1))

        assertThat(proof.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000667 (calc)┳00000630 (calc)   ┳00000000 (calc) known leaf
                           ┃                  ┗00000001 (calc) known leaf
                           ┗00000002 (input 0)━unknown         filtered
        """
        )
        val proof2 = proof.subset(trivialHashDigestProvider, listOf(0))
        assertThat(proof2.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000667 (calc)┳00000630 (calc)   ┳00000000 (calc)    known leaf
                           ┃                  ┗00000001 (input 0) filtered
                           ┗00000002 (input 1)━unknown            filtered
        """
        )
        val proof3 = proof.subset(trivialHashDigestProvider, listOf(1))
        assertThat(proof3.render(trivialHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            00000667 (calc)┳00000630 (calc)   ┳00000000 (input 0) filtered
                           ┃                  ┗00000001 (calc)    known leaf
                           ┗00000002 (input 1)━unknown            filtered
        """
        )
        assertFailsWith<IllegalArgumentException> {
            proof.subset(trivialHashDigestProvider, listOf(2))
        }
        assertFailsWith<IllegalArgumentException> {
            proof.subset(trivialHashDigestProvider, emptyList())
        }
    }


    @ParameterizedTest(name = "subset of tree size {0} proof with leaves {1} subset to {2}")
    @MethodSource("subsetTestCombinations")
    fun `test subset`(treeSize: Int, leafIndicesCombination: List<Int>, subsetProofLeafSet: List<Int>) {

        //val digest = trivialHashDigestProvider
        val digest = defaultHashDigestProvider
        val merkleTree = makeTestMerkleTree(treeSize, digest)
        val proof = makeProof(merkleTree, leafIndicesCombination)
        val missingLeaves = subsetProofLeafSet.filter { leaf -> leaf !in leafIndicesCombination }
        val missing = missingLeaves.isNotEmpty()

        logger.trace(
            "tree size {} source proof {} subset {} missing {} ({})",
            treeSize,
            leafIndicesCombination,
            subsetProofLeafSet,
            missingLeaves,
            missing
        )
        when {
            subsetProofLeafSet.isEmpty() ->
                // no leaves in output, which is never legal
                assertFailsWith<java.lang.IllegalArgumentException> {
                    proof.subset(digest, subsetProofLeafSet)
                }

            missing ->
                // there are leaves in subset which are not in the source proof, which should fail
                assertFailsWith<java.lang.IllegalArgumentException> {
                    proof.subset(digest, subsetProofLeafSet)
                }

            else ->
                proof.subset(digest, subsetProofLeafSet).also {

                    val text = it.render(digest)
                    logger.trace("subset proof $text")
                    assertThat(it.calculateRoot(digest)).isEqualTo(
                        proof.calculateRoot(
                            digest
                        )
                    )
                }
        }
    }


    private fun makeProof(
        merkleTree: MerkleTree,
        leafIndicesCombination: List<Int>
    ): MerkleProofImpl = merkleTree.createAuditProof(leafIndicesCombination) as MerkleProofImpl

    /**
     * Make a merkle tree for test purposes
     *
     * The leaf data will be successive integers starting at 0.
     *
     * @param treeSize the number of elements
     * @param hashProvider the functions used to hash the tree
     * @param contentBuilder an optional callback to generate each node in the tree
     * @return a MerkleTree object
     */
    private fun makeTestMerkleTree(
        treeSize: Int,
        hashProvider: MerkleTreeHashDigestProvider,
        contentBuilder: (index: Int) -> ByteArray = { it.toByteArray() }
    ): MerkleTree {
        // 1. make some leaf data, which will just be successive integers starting at zero
        val leafData = (0 until treeSize).map { contentBuilder(it) }
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

    private fun testProof(
        treeSize: Int,
        leaves: List<Int>,
        expected: String,
        digest: MerkleTreeHashDigestProvider = defaultHashDigestProvider
    ) {
        val tree = makeTestMerkleTree(treeSize, digest)
        val proof = makeProof(tree, leaves)
        assertThat(proof.render(digest)).isEqualToIgnoringWhitespace(expected)

        // The original root can be reconstructed from the proof
        assertEquals(proof.calculateRoot(digest), tree.root)
        assertTrue(proof.verify(tree.root, digest))

        // Wrong root should not be accepted.
        val wrongRootBytes = tree.root.bytes
        wrongRootBytes[0] = wrongRootBytes[0] xor 1
        val wrongRootHash = SecureHashImpl(DigestAlgorithmName.SHA2_256D.name, wrongRootBytes)
        assertFalse(proof.verify(wrongRootHash, digest))

        // We break the leaves one by one. All of them should break the proof.
        for (leaf in proof.leaves) {
            val data = leaf.leafData
            data[0] = data[0] xor 1
            assertFalse(proof.verify(tree.root, digest))
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
            assertFalse(badProof.verify(tree.root, digest))
        }

        // We add one extra hash which breaks the proof.
        val badProof1: MerkleProof =
            MerkleProofImpl(
                MerkleProofType.AUDIT, proof.treeSize, proof.leaves, proof.hashes + digestService.getZeroHash(
                    digestAlgorithm
                )
            )
        assertFalse(badProof1.verify(tree.root, digest))

        // We remove one hash which breaks the proof.
        if (proof.hashes.size > 1) {
            val badProof2: MerkleProof =
                MerkleProofImpl(
                    MerkleProofType.AUDIT,
                    proof.treeSize,
                    proof.leaves,
                    proof.hashes.take(proof.hashes.size - 1)
                )
            assertFalse(badProof2.verify(tree.root, digest))
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
            assertFalse(badProof3.verify(tree.root, digest))
        }

        // If there are leaves not have been added yet
        val notInProofLeaves = (0 until treeSize).filter { it !in leaves }
        if (notInProofLeaves.isNotEmpty()) {
            val extraIndex = notInProofLeaves.first()
            val extraLeaf = IndexedMerkleLeafImpl(
                extraIndex,
                trivialHashDigestProvider.leafNonce(extraIndex),
                tree.leaves[extraIndex]
            )

            // We add one leaf which breaks the proof.
            val badProofExtraLeaf: MerkleProof =
                MerkleProofImpl(proof.proofType, proof.treeSize, proof.leaves + extraLeaf, proof.hashes)
            assertFalse(badProofExtraLeaf.verify(tree.root, digest))

            // We replace one leaf which breaks the proof, since the leaves will not match the hashes.
            val badProofReplacedLeaf: MerkleProof =
                MerkleProofImpl(
                    MerkleProofType.AUDIT,
                    proof.treeSize,
                    proof.leaves.dropLast(1) + extraLeaf,
                    proof.hashes
                )
            assertFalse(badProofReplacedLeaf.verify(tree.root, digest))

        }

        // We duplicate one leaf which breaks the proof.
        val badProofDuplicateLeaf: MerkleProof =
            MerkleProofImpl(MerkleProofType.AUDIT, proof.treeSize, proof.leaves + proof.leaves.last(), proof.hashes)
        assertFalse(badProofDuplicateLeaf.verify(tree.root, digest))

    }

    @Test
    fun `Merkle proof size 1 with double SHA256`() {
        testProof(
            1, listOf(0), """
             7901AF93 (calc) known leaf
        """
        )
    }

    @Test
    fun `Merkle proof size 2 with trivial hash`() {
        testProof(
            2, listOf(0), """
            00000630 (calc)┳00000000 (calc)    known leaf
                           ┗00000001 (input 0) filtered
        """, trivialHashDigestProvider
        )
    }

    @Test
    fun `Merkle proof size 3 and leaf set 0 using SHA256D hashes`() {
        testProof(
            3, listOf(0), """
            A9D5543C (calc)┳BAB170B1 (calc)   ┳7901AF93 (calc)    known leaf
                           ┃                  ┗471864D3 (input 0) filtered
                           ┗66973B1A (input 1)━unknown            filtered
        """.trimIndent()
        )
    }

    @Test
    fun `Merkle proof size 6 with leaf set 0, 1 using SHA256D hashes`() {
        testProof(
            6, listOf(0, 1), """
            8696C1F4 (calc)┳FF3C3992 (calc)   ┳BAB170B1 (calc)   ┳7901AF93 (calc) known leaf
                           ┃                  ┃                  ┗471864D3 (calc) known leaf
                           ┃                  ┗517A5DE6 (input 0)┳unknown         filtered
                           ┃                                     ┗unknown         filtered
                           ┗E0CC7E23 (input 1)━unknown           ┳unknown         filtered
                                                                 ┗unknown         filtered        
        """.trimIndent()
        )
    }

    @Test
    fun `Merkle proof size 6 with leaf set 1, 3, 5 using trivial hashes`() {
        testProof(
            6, listOf(1, 3, 5), """
            00000612 (calc)┳0000069F (calc)┳00000630 (calc)┳00000000 (input 0) filtered
                           ┃               ┃               ┗00000001 (calc)    known leaf
                           ┃               ┗00000634 (calc)┳00000002 (input 1) filtered
                           ┃                               ┗00000003 (calc)    known leaf
                           ┗00000638 (calc)━00000638 (calc)┳00000004 (input 2) filtered
                                                           ┗00000005 (calc)    known leaf            
        """.trimIndent(), trivialHashDigestProvider
        )
    }

    @Test
    fun `Merkle proof size 6 with leaf set 3 using trivial hashes`() {
        testProof(
            6, listOf(4), """
                00000612 (calc)┳0000069F (input 1)┳unknown        ┳unknown            filtered
                               ┃                  ┃               ┗unknown            filtered
                               ┃                  ┗unknown        ┳unknown            filtered
                               ┃                                  ┗unknown            filtered
                               ┗00000638 (calc)   ━00000638 (calc)┳00000004 (calc)    known leaf
                                                                  ┗00000005 (input 0) filtered            
        """.trimIndent(), trivialHashDigestProvider
        )
    }

    @Test
    fun `merkle proof size 15 subset to leaf set 8 using trivial hashes`() {
        val treeSize = 15
        val sourceProofLeafSet = 28416
        val subsetProofLeafSet = 256
        val merkleTree = makeTestMerkleTree(treeSize, trivialHashDigestProvider)
        val treeText = renderTree((0 until treeSize).map { it.toString() }, emptyMap())
        assertThat(treeText).isEqualToIgnoringWhitespace(
            """
                ┳┳┳┳0
                ┃┃┃┗1
                ┃┃┗┳2
                ┃┃ ┗3
                ┃┗┳┳4
                ┃ ┃┗5
                ┃ ┗┳6
                ┃  ┗7
                ┗┳┳┳8
                 ┃┃┗9
                 ┃┗┳10
                 ┃ ┗11
                 ┗┳┳12
                  ┃┗13
                  ┗━14
        """.trimIndent()
        )
        val leafIndicesCombination = (0 until treeSize).filter { (sourceProofLeafSet and (1 shl it)) != 0 }
        val proof = makeProof(merkleTree, leafIndicesCombination)
        val proofText = proof.render(trivialHashDigestProvider)
        assertThat(proofText).isEqualToIgnoringWhitespace(
            """
            00000547 (calc)┳00000589 (input 1)┳unknown        ┳unknown        ┳unknown            filtered
                           ┃                  ┃               ┃               ┗unknown            filtered
                           ┃                  ┃               ┗unknown        ┳unknown            filtered
                           ┃                  ┃                               ┗unknown            filtered
                           ┃                  ┗unknown        ┳unknown        ┳unknown            filtered
                           ┃                                  ┃               ┗unknown            filtered
                           ┃                                  ┗unknown        ┳unknown            filtered
                           ┃                                                  ┗unknown            filtered
                           ┗00000585 (calc)   ┳000006BF (calc)┳00000640 (calc)┳00000008 (calc)    known leaf
                                              ┃               ┃               ┗00000009 (calc)    known leaf
                                              ┃               ┗00000644 (calc)┳0000000A (calc)    known leaf
                                              ┃                               ┗0000000B (calc)    known leaf
                                              ┗0000068B (calc)┳00000648 (calc)┳0000000C (input 0) filtered
                                                              ┃               ┗0000000D (calc)    known leaf
                                                              ┗0000000E (calc)━0000000E (calc)    known leaf
                                                          """.trimIndent()
        )
        val subLeafIndicesCombination = (0 until treeSize).filter { leaf -> (subsetProofLeafSet and (1 shl leaf)) != 0 }
        val subproof = proof.subset(trivialHashDigestProvider, subLeafIndicesCombination)
        val subproofText = subproof.render(trivialHashDigestProvider)
        assertThat(subproofText).isEqualToIgnoringWhitespace(
            """
            00000547 (calc)┳00000589 (input 3)┳unknown           ┳unknown           ┳unknown            filtered
                           ┃                  ┃                  ┃                  ┗unknown            filtered
                           ┃                  ┃                  ┗unknown           ┳unknown            filtered
                           ┃                  ┃                                     ┗unknown            filtered
                           ┃                  ┗unknown           ┳unknown           ┳unknown            filtered
                           ┃                                     ┃                  ┗unknown            filtered
                           ┃                                     ┗unknown           ┳unknown            filtered
                           ┃                                                        ┗unknown            filtered
                           ┗00000585 (calc)   ┳000006BF (calc)   ┳00000640 (calc)   ┳00000008 (calc)    known leaf
                                              ┃                  ┃                  ┗00000009 (input 0) filtered
                                              ┃                  ┗00000644 (input 1)┳unknown            filtered
                                              ┃                                     ┗unknown            filtered
                                              ┗0000068B (input 2)┳unknown           ┳unknown            filtered
                                                                 ┃                  ┗unknown            filtered
                                                                 ┗unknown           ━unknown            filtered
        """.trimIndent()
        )
    }

    @Test
    fun `subset 10 bug `() {
        val merkleTree = makeTestMerkleTree(10, defaultHashDigestProvider)
        val proof = makeProof(merkleTree, listOf(1, 2, 4, 5, 6, 7, 8))
        assertThat(proof.render(defaultHashDigestProvider)).isEqualToIgnoringWhitespace(
            """
            B79D89FF (calc)┳A868A19C (calc)┳FF3C3992 (calc)┳BAB170B1 (calc)┳7901AF93 (input 0) filtered
                             ┃               ┃               ┃               ┗471864D3 (calc)    known leaf
                             ┃               ┃               ┗517A5DE6 (calc)┳66973B1A (calc)    known leaf
                             ┃               ┃                               ┗568F8D2A (input 1) filtered
                             ┃               ┗EF03791A (calc)┳E0CC7E23 (calc)┳1D3A2328 (calc)    known leaf
                             ┃                               ┃               ┗CF5F6713 (calc)    known leaf
                             ┃                               ┗A1A26281 (calc)┳46086473 (calc)    known leaf
                             ┃                                               ┗B0D020DA (calc)    known leaf
                             ┗7954D9A4 (calc)━7954D9A4 (calc)━7954D9A4 (calc)┳A06E92D1 (calc)    known leaf
                                                                             ┗CB44AEFD (input 2) filtered            
        """
        )
        proof.subset(defaultHashDigestProvider, listOf(1, 7, 8)).also {
            val text = it.render(defaultHashDigestProvider)
            assertThat(text).isEqualToIgnoringWhitespace(
                """
                B79D89FF (calc)┳A868A19C (calc)┳FF3C3992 (calc)┳BAB170B1 (calc)   ┳7901AF93 (input 0) filtered
                               ┃               ┃               ┃                  ┗471864D3 (calc)    known leaf
                               ┃               ┃               ┗517A5DE6 (input 3)┳unknown            filtered
                               ┃               ┃                                  ┗unknown            filtered
                               ┃               ┗EF03791A (calc)┳E0CC7E23 (input 4)┳unknown            filtered
                               ┃                               ┃                  ┗unknown            filtered
                               ┃                               ┗A1A26281 (calc)   ┳46086473 (input 1) filtered
                               ┃                                                  ┗B0D020DA (calc)    known leaf
                               ┗7954D9A4 (calc)━7954D9A4 (calc)━7954D9A4 (calc)   ┳A06E92D1 (calc)    known leaf
                                                                                  ┗CB44AEFD (input 2) filtered   
            """
            )
            logger.trace("subset proof $text")
            assertThat(it.calculateRoot(defaultHashDigestProvider)).isEqualTo(
                proof.calculateRoot(
                    defaultHashDigestProvider
                )
            )
        }
    }

    @Test
    fun `test merge 10 bug`() {
        val xSubset = listOf(1, 7, 8)
        val ySubset = listOf(2, 4, 5, 6)
        val leafIndicesCombination = xSubset + ySubset
        val merkleTree = makeTestMerkleTree(10, defaultHashDigestProvider)
        val proof = makeProof(merkleTree, leafIndicesCombination)
        val proofText = proof.render(defaultHashDigestProvider)
        check(xSubset.size + ySubset.size == leafIndicesCombination.size)
        val xProof = proof.subset(defaultHashDigestProvider, xSubset)
        val yProof = proof.subset(defaultHashDigestProvider, ySubset)
        val mergedProof = xProof.merge(yProof, defaultHashDigestProvider) as MerkleProofImpl
        val mergedProofText = mergedProof.render(defaultHashDigestProvider)
        assertThat(mergedProofText).isEqualTo(proofText)
    }

    @Test
    fun `test rejection of merge when leaves differ`() {
        val tree1 = makeTestMerkleTree(3, trivialHashDigestProvider) { it.toByteArray() } as MerkleTreeImpl
        assertThat(tree1.render()).isEqualToIgnoringWhitespace("""
            00000667┳00000630┳00000000 00:00:00:00
                    ┃        ┗00000001 00:00:00:01
                    ┗00000002━00000002 00:00:00:02
        """.trimIndent())
        val tree2 = makeTestMerkleTree(3, trivialHashDigestProvider) { (256 * 0x42 + it).toByteArray() } as MerkleTreeImpl
        assertThat(tree2.render()).isEqualToIgnoringWhitespace("""
            0000062d┳000006b4┳00000042 00:00:42:00
                    ┃        ┗00000043 00:00:42:01
                    ┗00000044━00000044 00:00:42:02
        """.trimIndent())
        val xProof = makeProof(tree1, listOf(0,1))
        val yProof = makeProof(tree2, listOf(1,2))
        val ex = assertFailsWith<IllegalArgumentException> { xProof.merge(yProof, trivialHashDigestProvider) }
        assertThat(ex.message).contains("common leaves in a proof merge must match")
    }

    @Test
    fun `test merge tree size 2 with 3 fails`() {
        val merkleTree2 = makeTestMerkleTree(2, defaultHashDigestProvider)
        val merkleTree3 = makeTestMerkleTree(3, defaultHashDigestProvider)
        val xProof = makeProof(merkleTree2, listOf(0))
        val yProof = makeProof(merkleTree3, listOf(1))
        val ex = assertFailsWith<IllegalArgumentException> {  xProof.merge(yProof, defaultHashDigestProvider) }
        assertThat(ex.message).contains("underlying tree sizes must match")
        assertThat(ex.message).contains("left hand side has underlying tree size 2")
        assertThat(ex.message).contains("right hand side has underlying tree size 3")
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
    assertThat((actual as MerkleTreeImpl).render()).isEqualTo(expectedRendered.trimIndent())
