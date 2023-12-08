package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofRebuildFailureException


/**
 * Work out the hashes required to fulfill a proof, and returned them
 * with their levels and tree indices.
 *
 * @param proof A valid Merkle proof
 * @param digest An object that computes hashes for merkle tree elements
 * @return
 */
fun calculateLeveledHashes(proof: MerkleProof, digest: MerkleTreeHashDigestProvider): List<LeveledHash> {
    require(proof.leaves.isNotEmpty(), {"MerkleProof must have leaves"})
    require(proof.leaves.all { it.index >= 0 && it.index < proof.treeSize }, {"MerkleProof leaves cannot point outside of the original tree."})
    require(proof.leaves.map { it.index }.toSet().size == proof.leaves.size, {"MerkleProof leaves cannot have duplications."})
    val hashes = proof.hashes
    val treeSize = proof.treeSize
    val leaves = proof.leaves
    var hashIndex = treeSize
    val sortedLeaves = leaves.sortedBy { it.index }
    var nodeHashes = sortedLeaves.map { Pair(it.index, digest.leafHash(it.index, it.nonce, it.leafData)) }
    val leveledHashes = mutableListOf<LeveledHash>()
    var treeDepth = MerkleTreeImpl.treeDepth(treeSize)
    var currentSize = treeSize
    while (currentSize > 1) {
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
                newItems += if ((item.first and 1) == 0) {      // Even index means, that the item is on the left
                    leveledHashes += LeveledHash(treeDepth, item.first + 1, hashes[hashIndex])
                    Pair(
                        item.first / 2,
                        digest.nodeHash(treeDepth, item.second, hashes[hashIndex++])
                    )
                } else {                                        // Odd index means, that the item is on the right
                    leveledHashes += LeveledHash(treeDepth, item.first - 1, hashes[hashIndex])
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
    return leveledHashes
}
