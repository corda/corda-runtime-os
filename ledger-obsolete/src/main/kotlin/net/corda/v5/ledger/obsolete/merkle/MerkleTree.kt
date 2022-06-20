package net.corda.v5.ledger.obsolete.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash

/**
 * Creation and verification of a Merkle tree.
 *
 * See: https://en.wikipedia.org/wiki/Merkle_tree
 *
 * Transaction is split into following blocks: inputs, attachments' refs, outputs, commands, notary,
 * signers, tx type, time-window. Merkle Tree is kept in a recursive data structure. Building is done bottom up,
 * from all leaves' hashes. If number of leaves is not a power of two, the tree is padded with zero hashes.
 */
sealed class MerkleTree {
    abstract val hash: SecureHash

    data class Leaf(override val hash: SecureHash) : MerkleTree()
    data class Node(override val hash: SecureHash, val left: MerkleTree, val right: MerkleTree) : MerkleTree()

    companion object {
        private fun isPow2(num: Int): Boolean = num and (num - 1) == 0

        /**
         * Merkle tree building using hashes, with zero hash padding to full power of 2.
         *
         * @throws MerkleTreeException if [allLeavesHashes] is empty.
         */
        @JvmStatic
        fun getMerkleTree(
            allLeavesHashes: List<SecureHash>,
            nodeDigestAlgorithmName: DigestAlgorithmName,
            digestService: DigestService
        ): MerkleTree {
            if (allLeavesHashes.isEmpty())
                throw MerkleTreeException("Cannot calculate Merkle root on empty hash list.")
            val algorithms = allLeavesHashes.mapTo(HashSet(), SecureHash::algorithm)
            require(algorithms.size == 1) {
                "Cannot build Merkle tree with multiple hash algorithms: $algorithms"
            }
            val leaves = padWithZeros(allLeavesHashes, digestService).map { Leaf(it) }
            return buildMerkleTree(leaves, nodeDigestAlgorithmName, digestService)
        }

        // If number of leaves in the tree is not a power of 2, we need to pad it with zero hashes.
        private fun padWithZeros(allLeavesHashes: List<SecureHash>, digestService: DigestService): List<SecureHash> {
            var n = allLeavesHashes.size
            if (n > 1 && isPow2(n)) return allLeavesHashes
            val paddedHashes = ArrayList(allLeavesHashes)
            val zeroHash = zeroHash(digestService, DigestAlgorithmName((paddedHashes[0].algorithm)))
            do {
                paddedHashes.add(zeroHash)
            } while (!isPow2(++n))
            return paddedHashes
        }

        /**
         * Tailrecursive function for building a tree bottom up.
         * @param lastNodesList MerkleTree nodes from previous level.
         * @return Tree root.
         */
        private tailrec fun buildMerkleTree(
            lastNodesList: List<MerkleTree>,
            nodeDigestAlgorithmName: DigestAlgorithmName,
            digestService: DigestService
        ): MerkleTree {
            return if (lastNodesList.size == 1) {
                lastNodesList[0] // Root reached.
            } else {
                val newLevelHashes: MutableList<MerkleTree> = ArrayList()
                val n = lastNodesList.size
                require((n and 1) == 0) { "Sanity check: number of nodes should be even." }
                for (i in 0..n - 2 step 2) {
                    val left = lastNodesList[i]
                    val right = lastNodesList[i + 1]
                    val node = Node(digestService.hash(left.hash.bytes + right.hash.bytes, nodeDigestAlgorithmName), left, right)
                    newLevelHashes.add(node)
                }
                buildMerkleTree(newLevelHashes, nodeDigestAlgorithmName, digestService)
            }
        }

        private fun zeroHash(digestService: DigestService, algorithmName: DigestAlgorithmName): SecureHash =
            SecureHash(algorithmName.name, ByteArray(digestService.digestLength(algorithmName)) { 0.toByte() })
    }
}