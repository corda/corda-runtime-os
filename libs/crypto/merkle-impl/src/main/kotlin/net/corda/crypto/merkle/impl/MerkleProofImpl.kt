package net.corda.crypto.merkle.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofRebuildFailureException
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest
import org.slf4j.LoggerFactory

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

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

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
    override fun calculateRoot(digest: MerkleTreeHashDigest): SecureHash = calculateRootInstrumented(digest)


    fun calculateRootInstrumented(digest: MerkleTreeHashDigest, foundHashCallback: (hash: SecureHash, level: Int, nodeIndex: Int, consumed: Int?) -> Unit = { _, _, _, _ ->  } ): SecureHash {
        if (digest !is MerkleTreeHashDigestProvider) {
            throw CordaRuntimeException(
                "An instance of MerkleTreeHashDigestProvider is required when " +
                        "verifying a Merkle root, but received ${digest.javaClass.name} instead."
            )
        }

        // We do support and test with leaves.size == treeSize, which may not
        // be very useful but needed not be a special case.
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
        // work out nodeHashes, which is a list of node information for the current level we operate at
        var nodeHashes: List<Pair<Int, SecureHash>> =
            sortedLeaves.map { Pair(it.index, digest.leafHash(it.index, it.nonce, it.leafData)) }
        var treeDepth = MerkleTreeImpl.treeDepth(treeSize)         // initialised to the depth of tree we should
        nodeHashes.forEach { item -> foundHashCallback(item.second, treeDepth, item.first, null)  }
        // need for the number of elements
        var currentSize = treeSize                                 // outer loop variable; the number of
        // leaves left as we roll up the tree

        // loop over each level of the tree, starting at the deepest level (i.e. furthest from root)
        while (currentSize > 1) {
            // Process a level of the tree which means generating the hashes for the level above (i.e. closer
            // to the root).

            // There'd be nothing to do if the tree size $currentSize is 1, hence the loop condition
            if (nodeHashes.isEmpty()) {
                throw MerkleProofRebuildFailureException(
                    "MerkleProof does not have enough nodeHashes to calculate root hash."
                )
            }
            --treeDepth
            // We could check here that size of nodeHashes is as expected for treeDepth; there should be a closed form.

            // ... so that's 4 variables that get updated as we work:
            // - $hashIndex is the position we are at in the supplied proof hashes
            // - $currentSize is the number of leafs+nodes at this level of the tree
            // - $treeDepth is the level of the tree, counting from the root of the tree where $currentSize==1
            // - $nodeHashes has a list of pairs of the index and hash of the node. We checked we have some content.

            val newItems = mutableListOf<Pair<Int, SecureHash>>()   // this will become nodeHashes at the end of this
            // out iteration

            // Now walk over the hashes at this tree level, striding over 1 or 2 at a time
            var index = 0
            while (index < nodeHashes.size) {
                val item = nodeHashes[index]
                // We are at level $treeDepth from the top of the tree (where 1 is the root of the tree),
                //     and at $index nodes from the left (counting from 0)
                // $item is a pair of the index and the hash at the index.
                //
                // Since index == item.first we don't really need to use item.first

                if (item.first < currentSize and 0x7FFFFFFE) {      // If the level has odd elements, we'll process
                    // the last element later.
                    if (index < nodeHashes.size - 1) {              // If there is a next element...
                        val next = nodeHashes[index + 1]
                        // Decide if we can consume the next two elements since they are adjacent in the Merkle tree
                        if (item.first xor next.first == 1) {       // ... and they are a pair with the current
                            // We now know that the indices ${item.first} and ${next.first} only differ on the bottom bit,
                            // i.e. they are adjacent. Therefore we can combine them.

                            val newHash = digest.nodeHash(treeDepth, item.second, next.second)
                            foundHashCallback( newHash, treeDepth, newItems.size, null)
                            // So, make a single new item, computing a new hash
                            // (Pair is the Kotlin type, nothing to do with pairing nodes)
                            // in the original tree, we create their parent.
                            newItems += Pair(item.first / 2, newHash)
                            // and record that we consumed two values from our working set, and skip on to the
                            // start of the next loop
                            index += 2                              // we've consumed two
                            continue                                // continue the inner level scanning loop
                        }
                    }
                    // The continue above in the previous section mean we skip the rest of this section if we
                    // chose to make a new node by combining two known hashes.

                    // At this point we know we do not know enough to simply take two known hashes at $index and ${index+1} and roll
                    // them up, so we are going to have to consume a hash.

                    if (hashIndex >= hashes.size) {                 // We'll need one more hash to continue. So if
                        throw MerkleProofRebuildFailureException(   // we do not have more, the proof is incorrect.
                            "MerkleProof root calculation requires more hashes than the proof has."
                        )
                    }
                    val newIndex = item.first /2

                    // We pair the current element with a hash from the proof
                    newItems += if ((item.first and 1) == 0) {      // Even index means, that the item is on the left
                        // Make new node with
                        //   - left being current element, index $item.first, hash $item.second
                        //   - right being a consumed incoming hash from $hashes[$hashIndex]
                        //
                        // Also remember we used $hashIndex by bumping the counter
                        foundHashCallback(hashes[hashIndex], treeDepth+1, item.first + 1, hashIndex)
                        val newHash = digest.nodeHash(treeDepth, item.second, hashes[hashIndex++])
                        foundHashCallback( newHash, treeDepth, newIndex, null)
                        Pair(newIndex, newHash)
                    } else {
                        // Make new node with:
                        //   - left being proof of hash at $hashIndex
                        //   - right being current element, index $item.first, hash $item.second
                        //
                        // Also remember we used hashIndex by bumping the counter.
                        foundHashCallback(hashes[hashIndex], treeDepth+1, item.first-1, hashIndex)
                        val newHash = digest.nodeHash(treeDepth, hashes[hashIndex++], item.second)
                        foundHashCallback( newHash, treeDepth, newIndex, null)
                        Pair(newIndex, newHash)
                    }
                } else {
                    val newIndex = (item.first + 1) / 2
                    foundHashCallback( item.second, treeDepth, newIndex, null)
                    // The last odd element, just gets lifted.
                    newItems += Pair(newIndex, item.second)
                }
                ++index // whatever of the last 3 cases we took, we consumed one element
            }
            // now we move up a level, so the tree gets smaller...
            currentSize = (currentSize + 1) / 2
            // and we have a new set of known elements
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
        //foundHashCallback( nodeHashes.single().second, 0, 0, null)
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

    override fun toString(): String = renderTree( (0 until treeSize).map {
            "$it "+(if (it in getLeaves().map { l -> l.index }) "known data" else "gap")
        })

    fun illustrate(digest: MerkleTreeHashDigest): String {
        val nodeLabels: MutableMap<Pair<Int,Int>, String> = mutableMapOf()
        calculateRootInstrumented(digest) { hash, level, nodeIndex, consumed ->
            nodeLabels[level to nodeIndex] = hash.toString().substringAfter(":").take(8) + (if (consumed!=null) " (input $consumed)" else " (calc)")
        }
        val treeDepth = MerkleTreeImpl.treeDepth(treeSize)
        val leafLabels = (0 until treeSize).map {
            "${nodeLabels[treeDepth to it]?:"unknown"} $it " + (if (it in getLeaves().map { l -> l.index }) "known data" else "gap")
        }
        return renderTree(leafLabels, nodeLabels)
    }
}
