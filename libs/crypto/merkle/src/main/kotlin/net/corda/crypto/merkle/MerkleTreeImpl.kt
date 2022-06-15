package net.corda.crypto.merkle

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider

internal class MerkleTreeImpl(
    override val leaves: List<ByteArray>,
    override val digestProvider: MerkleTreeHashDigestProvider
) : MerkleTree {

    init {
        require(leaves.isNotEmpty()) { "Merkle tree must have at least one item" }
    }

    companion object {
        @JvmStatic
        fun createMerkleTree(
            leaves: List<ByteArray>,
            digestProvider: MerkleTreeHashDigestProvider
        ): MerkleTreeImpl = MerkleTreeImpl(leaves, digestProvider)

        fun nextHigherPower2(value: Int): Int {
            require(value > 0) { "nextHigherPower2 requires positive value" }
            require(value <= 0x40000000) { "nextHigherPower2 requires smaller value" }
            var v = value - 1
            v = v or (v ushr 1)
            v = v or (v ushr 2)
            v = v or (v ushr 4)
            v = v or (v ushr 8)
            v = v or (v ushr 16)
            return v + 1
        }

        fun treeDepth(size: Int): Int {
            val zeros = nextHigherPower2(size).countLeadingZeroBits()
            return 31 - zeros
        }

    }

    private val leafHashes: List<SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        leaves.mapIndexed { index, bytes ->
            val nonce = digestProvider.leafNonce(index)
            digestProvider.leafHash(index, nonce, bytes)
        }
    }

    private val nodeHashes: List<List<SecureHash>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val hashSet = mutableListOf<List<SecureHash>>()
        var hashes = leafHashes
        hashSet += hashes
        var depthCounter = depth
        while (hashes.size > 1) {
            --depthCounter
            val nodeHashes = mutableListOf<SecureHash>()
            for (i in hashes.indices step 2) {
                if (i <= hashes.size - 2) {
                    nodeHashes += digestProvider.nodeHash(depthCounter, hashes[i], hashes[i + 1])
                }
            }
            if ((hashes.size and 1) == 1) {
                nodeHashes += hashes.last()
            }
            hashes = nodeHashes
            hashSet += hashes
        }
        require(depthCounter == 0) { "Sanity check root is at depth 0" }
        hashSet
    }

    private val depth: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        treeDepth(leaves.size)
    }

    override val root: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        nodeHashes.last().single()
    }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    override fun createAuditProof(leafIndices: List<Int>): MerkleProof {
        require(leafIndices.isNotEmpty()) { "Proof requires at least one leaf" }
        require(leafIndices.all { it >= 0 && it < leaves.size }) { "Leaf indices out of bounds" }
        var inPath = List(leaves.size) { it in leafIndices }
        val outputHashes = mutableListOf<SecureHash>()
        var level = 0
        while (inPath.size > 1) {
            val newInPath = mutableListOf<Boolean>()
            for (i in inPath.indices step 2) {
                if (i <= inPath.size - 2) {
                    newInPath += inPath[i] || inPath[i + 1]
                    if (!inPath[i] && inPath[i + 1]) {
                        outputHashes += nodeHashes[level][i]
                    } else if (inPath[i] && !inPath[i + 1]) {
                        outputHashes += nodeHashes[level][i + 1]
                    }
                }
            }
            if ((inPath.size and 1) == 1) {
                newInPath += inPath.last()
            }
            inPath = newInPath
            ++level
        }
        require(level == depth) { "Sanity check calc" }
        return MerkleProofImpl(
            leaves.size,
            leafIndices.sorted().map { IndexedMerkleLeaf(it, digestProvider.leafNonce(it), leaves[it].copyOf()) },
            outputHashes
        )
    }
}

