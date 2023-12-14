package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofRebuildFailureException


/**
 * Work out the hashes required to fulfill a proof, and return them
 * with their levels and tree indices.
 *
 * @param proof A valid Merkle proof
 * @param digest An object that computes hashes for merkle tree elements
 * @return a list of LeveledHash objects, one entry with level information for each hash consumed in the proof.
 */
@Suppress("NestedBlockDepth", "ThrowsCount")
fun calculateLeveledHashes(proof: MerkleProof, digest: MerkleTreeHashDigestProvider): List<LeveledHash> {
    require(proof.leaves.isNotEmpty(), {"MerkleProof must have leaves"})
    require(proof.leaves.all { it.index >= 0 && it.index < proof.treeSize }) {
        "MerkleProof leaves cannot point outside of the original tree."
    }
    require(proof.leaves.map { it.index }.toSet().size == proof.leaves.size, {"MerkleProof leaves cannot have duplications."})
    val hashes = proof.hashes
    if (proof.hashes.isEmpty()) return emptyList()
    val treeSize = proof.treeSize
    val leaves = proof.leaves

    var hashIndex = 0
    val sortedLeaves = leaves.sortedBy { it.index }
    var nodeHashes = sortedLeaves.map { Pair(it.index, digest.leafHash(it.index, it.nonce, it.leafData)) }
    val leveledHashes = mutableListOf<LeveledHash>() // output accumulator
    var treeDepth = MerkleTreeImpl.treeDepth(treeSize)
    var currentSize = treeSize

    // ... so we have 4 variables that get updated as we work:
    // - $hashIndex is the position we are at in the supplied proof hashes
    // - $currentSize is the number of leafs+nodes at this level of the tree
    // - $treeDepth is the level of the tree, counting from the root of the tree where $currentSize==1
    // - $nodeHashes has a list of pairs of the index and hash of the node. We checked we have some content.

    while (currentSize > 1) {
        // Now walk over the hashes at this tree level, striding over 1 or 2 at a time
        // We are at level $treeDepth from the top of the tree (where 1 is the roof of the tree),
        //     and at $index nodes from the left (counting from 0)
        // $item is a pair of the index and the hash at the index.
        //
        // Since index == item.first we don't really need to use item.first
        if (nodeHashes.isEmpty()) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof does not have enough nodeHashes to calculate root hash."
            )
        }
        --treeDepth
        val newItems = mutableListOf<Pair<Int, SecureHash>>()
        var index = 0 // index into node hashes, which starts off with an entry per leaf
        while (index < nodeHashes.size) {
            val item = nodeHashes[index]
            // Decide if we can consume the next two elements since they are adjancent in the Merkle tree
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
                // We skip the rest of this section if we chose to make a new node by combining two known hashes.

                // At this point we know we do not know enough to simply take two known hashes at $index and ${index+1} and roll
                // them up, so we are going to have to consume a hash.

                if (hashIndex > hashes.size) {                 // We'll need one more hash to continue. So if
                    throw MerkleProofRebuildFailureException(   // we do not have more, the proof is incorrect.
                        "MerkleProof root calculation requires more hashes than the proof has."
                    )
                }

                newItems += if ((item.first and 1) == 0) {      // Even index means, that the item is on the left
                    // Make new node with
                    //   - left being current element, index $item.first, hash $item.second
                    //   - right being proof of hash at $hashIndex
                    //
                    // Also remember we used hashIndex by bumping the counter
                    leveledHashes += LeveledHash(treeDepth, item.first + 1, hashes[hashIndex])
                    Pair(
                        item.first / 2,
                        digest.nodeHash(treeDepth, item.second, hashes[hashIndex++])
                    )
                } else {                                        // Odd index means, that the item is on the right
                    // Make new node with:
                    //   - left being proof of hash at $hashIndex
                    //   - right being current element, index $item.first, hash $item.second
                    //
                    // Also remember we used hashIndex by bumping the counter.
                    leveledHashes += LeveledHash(treeDepth, item.first - 1, hashes[hashIndex])
                    Pair(
                        item.first / 2,
                        digest.nodeHash(treeDepth, hashes[hashIndex++], item.second)
                    )
                }
            } else {                                            // The last odd element, just gets lifted.
                newItems += Pair((item.first + 1) / 2, item.second)
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
    return leveledHashes
}
