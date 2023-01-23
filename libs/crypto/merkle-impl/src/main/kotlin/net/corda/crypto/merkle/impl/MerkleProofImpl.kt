package net.corda.crypto.merkle.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest

class MerkleProofImpl(
    override val proofType: MerkleProofType,
    override val treeSize: Int,
    override val leaves: List<IndexedMerkleLeaf>,
    override val hashes: List<SecureHash>
) : MerkleProof {

    // CORE-5111: add serialize/deserialize (and its test)

    @Suppress("NestedBlockDepth", "ComplexMethod")
    /**
     * The verification process reconstructs the Merkle tree's root element from the proof.
     * Then it compares it with the input parameter to check if they match.
     *
     * It walks through the levels of the tree from bottom to up following the same logic as the
     * proof or tree creation. [MerkleTreeImpl.createAuditProof]
     * It recreates the routes towards the root element from the items in the leaves to be proven with using
     * the proof's hashes when they are needed.
     */
    override fun calculateRoot(digest: MerkleTreeHashDigest): SecureHash? {
        if (digest !is MerkleTreeHashDigestProvider) {
            throw CordaRuntimeException(
                "An instance of MerkleTreeHashDigestProvider is required when " +
                        "verifying a Merkle root, but received ${digest.javaClass.name} instead."
            )
        }

        if (leaves.isEmpty()) {
            return null
        }
        if (leaves.any { it.index < 0 || it.index >= treeSize }) {
            return null
        }
        if (leaves.map { it.index }.toSet().size != leaves.size) {
            return null
        }
        var hashIndex = 0
        val sortedLeaves = leaves.sortedBy { it.index }
        var nodeHashes = sortedLeaves.map { Pair(it.index, digest.leafHash(it.index, it.nonce, it.leafData)) }
        var treeDepth = MerkleTreeImpl.treeDepth(treeSize)
        var currentSize = treeSize
        while (currentSize > 1) {
            if (nodeHashes.isEmpty()) {
                return null
            }
            --treeDepth
            val newItems = mutableListOf<Pair<Int, SecureHash>>()
            var index = 0
            while (index < nodeHashes.size) {
                val item = nodeHashes[index]
                if (item.first < currentSize and 0x7FFFFFFE) {      // If the level has odd elements, we'll process
                                                                    // the last element later.
                    if (index < nodeHashes.size - 1) {              // If there is a next element...
                        val next = nodeHashes[index + 1]
                        if (item.first xor next.first == 1) {       // ... and they are a pair with the current
                            newItems += Pair(                       // in the original tree, we create their parent.
                                item.first / 2,
                                digest.nodeHash(treeDepth, item.second, next.second)
                            )
                            index += 2
                            continue
                        }
                    }
                    if (hashIndex >= hashes.size) {                 // We'll need one more hash to continue. So if
                        return null                                 // we do not have more, the proof is incorrect.
                    }
                                                                    // We pair the current element with a
                                                                    // hash from the proof
                    newItems += if ((item.first and 1) == 0) {      // Even index means, that the item is on the left
                        Pair(
                            item.first / 2,
                            digest.nodeHash(treeDepth, item.second, hashes[hashIndex++])
                        )
                    } else {                                        // Odd index means, that the item is on the right
                        Pair(
                            item.first / 2,
                            digest.nodeHash(treeDepth, hashes[hashIndex++], item.second)
                        )
                    }
                } else {                                            // The last odd element, just gets lifted.
                    newItems += Pair((item.first + 1) / 2, item.second)
                }
                ++index
            }
            currentSize = (currentSize + 1) / 2
            nodeHashes = newItems
        }
        if (hashIndex != hashes.size) {
            return null
        }
        if (nodeHashes.size != 1) {
            return null
        }
        return nodeHashes.single().second
    }

    override fun verify(root: SecureHash, digest: MerkleTreeHashDigest) =
        calculateRoot(digest) == root

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MerkleProofImpl

        if (treeSize != other.treeSize) return false
        if (leaves != other.leaves) return false
        if (hashes != other.hashes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = treeSize
        result = 31 * result + leaves.hashCode()
        result = 31 * result + hashes.hashCode()
        return result
    }
}