package net.corda.crypto.merkle.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofRebuildFailureException
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest

/**
 * Represent a merkle proof, which shows that some leaf data is in a Merkle tree.
 *
 * @param proofType
 * @param treeSize - total number of leaves in the Merkle tree
 * @param leaves - list of leaves for which we have data
 * @param hashes - the hashes needed to rebuild the parts of the tree where data is not given
 *
 * The number of elements in hashes will depend on the tree size and where in the tree the unknown
 * data is. There will need to be at least one, to cover the gap left by missing data. There never
 * needs to be more than the tree size minus the number of leaves specified.
 */

class MerkleProofImpl(
    private val proofType: MerkleProofType,
    private val treeSize: Int,
    private val leaves: List<IndexedMerkleLeaf>,
    private val hashes: List<SecureHash>
) : MerkleProof {

    // CORE-5111: add serialize/deserialize (and its test)

    override fun verify(root: SecureHash, digest: MerkleTreeHashDigest) =
        try {
            calculateRoot(digest) == root
        } catch (e: Exception) {
            false
        }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    /**
     * The verification process reconstructs the root element of the Merkle tree from the proof.
     * Then it compares it with the input parameter to check if they match.
     *
     * It walks through the levels of the tree from bottom to up following the same logic as the
     * proof or tree creation. [MerkleTreeImpl.createAuditProof]
     * It recreates the routes towards the root element from the items in the leaves to be proven with using
     * the proof hashes when they are needed.
     */
    override fun calculateRoot(digest: MerkleTreeHashDigest): SecureHash {
        if (digest !is MerkleTreeHashDigestProvider) {
            throw CordaRuntimeException(
                "An instance of MerkleTreeHashDigestProvider is required when " +
                        "verifying a Merkle root, but received ${digest.javaClass.name} instead."
            )
        }

        // we do support and test with leaves.size == treeSize, which may not
        // be very useful but needed not be a special case
        if (leaves.size > treeSize) {
            throw MerkleProofRebuildFailureException("MerkleProof has too many specified keys ${leaves.size} tree size ${treeSize}")
        }
        if (leaves.size < treeSize && hashes.isEmpty()) {
            throw MerkleProofRebuildFailureException("No fill-in hashes specified to MerkleProof")
        }
        if (hashes.size > treeSize + leaves.size) {
            throw MerkleProofRebuildFailureException("More MerkleProof non-data hashes given than is possibily necessary")
        }
        if (leaves.isEmpty()) {
            throw MerkleProofRebuildFailureException("MerkleProof should have at least one leaf.")
        }
        if (leaves.any { it.index < 0 || it.index >= treeSize }) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof leaves cannot point outside of the original tree."
            )
        }
        if (leaves.map { it.index }.toSet().size != leaves.size) {
            throw MerkleProofRebuildFailureException("MerkleProof leaves cannot have duplications.")
        }
        var hashIndex = 0
        val sortedLeaves = leaves.sortedBy { it.index }
        // work out nodeHashes, which is a map from leaf index to digest
        var nodeHashes = sortedLeaves.map { Pair(it.index, digest.leafHash(it.index, it.nonce, it.leafData)) }
        var treeDepth = MerkleTreeImpl.treeDepth(treeSize)
        var currentSize = treeSize
        println("calculateRoot initial tree size ${treeSize} leaves indices ${leaves.map { it.index }} |hashes|=${hashes.size}")

        while (currentSize > 1) {
            println("\touter loop currentSize=${currentSize} treeDepth=${treeDepth} |nodeHashes|=${nodeHashes.size}" )
            if (nodeHashes.isEmpty()) {
                throw MerkleProofRebuildFailureException(
                    "MerkleProof does not have enough nodeHashes to calculate root hash."
                )
            }
            --treeDepth
            val newItems = mutableListOf<Pair<Int, SecureHash>>()
            var index = 0
            while (index < nodeHashes.size) {
                val item = nodeHashes[index]
                println("\t\tinner loop at index ${index} nodehash.first=${item.first}")

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
                        throw MerkleProofRebuildFailureException(   // we do not have more, the proof is incorrect.
                            "MerkleProof root calculation requires more hashes than the proof has."
                        )
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
            throw MerkleProofRebuildFailureException(
                "MerkleProof root hash calculation used $hashIndex hashes instead of the available ${hashes.size}."
            )
        }
        if (nodeHashes.size != 1) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof root hash calculation ended with ${nodeHashes.size} node hashes instead of one."
            )
        }
        return nodeHashes.single().second
    }

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

    override fun getProofType() = proofType

    override fun getTreeSize() = treeSize

    override fun getLeaves() = leaves

    override fun getHashes() = hashes
}
