package net.corda.crypto.merkle

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider

internal class MerkleProofImpl(
    override val treeSize: Int,
    override val leaves: List<IndexedMerkleLeaf>,
    override val hashes: List<SecureHash>
) : MerkleProof {

    // CORE-5111: add serialize/deserialize (and its test)

    override fun verify(root: SecureHash, digestProvider: MerkleTreeHashDigestProvider): Boolean {
        if (leaves.isEmpty()) {
            return false
        }
        if (leaves.any { it.index < 0 || it.index >= treeSize }) {
            return false
        }
        if (leaves.map { it.index }.toSet().size != leaves.size) {
            return false
        }
        var hashIndex = 0
        val sortedLeaves = leaves.sortedBy { it.index }
        var nodeHashes = sortedLeaves.map { Pair(it.index, digestProvider.leafHash(it.index, it.nonce, it.leafData)) }
        var treeDepth = MerkleTreeImpl.treeDepth(treeSize)
        var currentSize = treeSize
        while (currentSize > 1) {
            if (nodeHashes.isEmpty()) {
                return false
            }
            --treeDepth
            val newItems = mutableListOf<Pair<Int, SecureHash>>()
            var index = 0
            while (index < nodeHashes.size) {
                val item = nodeHashes[index]
                if (item.first < currentSize and 0x7FFFFFFE) {
                    if (index < nodeHashes.size - 1) {
                        val next = nodeHashes[index + 1]
                        if (item.first xor next.first == 1) {
                            newItems += Pair(
                                item.first / 2,
                                digestProvider.nodeHash(treeDepth, item.second, next.second)
                            )
                            index += 2
                            continue
                        }
                    }
                    if (hashIndex >= hashes.size) {
                        return false
                    }
                    newItems += if (item.first and 1 == 0) {
                        Pair(
                            item.first / 2,
                            digestProvider.nodeHash(treeDepth, item.second, hashes[hashIndex++])
                        )
                    } else {
                        Pair(
                            item.first / 2,
                            digestProvider.nodeHash(treeDepth, hashes[hashIndex++], item.second)
                        )
                    }
                } else {
                    newItems += Pair((item.first + 1) / 2, item.second)
                }
                ++index
            }
            currentSize = (currentSize + 1) / 2
            nodeHashes = newItems
        }
        if (hashIndex != hashes.size) {
            return false
        }
        if (nodeHashes.size != 1) {
            return false
        }
        return nodeHashes.single().second == root
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
}