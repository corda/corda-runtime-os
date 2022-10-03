package net.corda.v5.crypto.extensions.merkle

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest

/**
 * Different use cases require different [MerkleTree] calculations.
 * [MerkleTreeHashDigestProvider]s let us specify the
 *  - Leaf Nonce
 *  - Leaf Hash
 *  - Node Hash calculation methods
 *  - Base Digest Algorithm
 *
 *  @property digestAlgorithmName Specifies the digest algorithm.
 */

interface MerkleTreeHashDigestProvider : MerkleTreeHashDigest  {
    /**
     * Calculates the nonce for a leaf.
     * @param index The leaf's index.
     */
    fun leafNonce(index: Int): ByteArray?

    /**
     * Calculates the hash of a leaf.
     * @param index The leaf's index.
     * @param nonce The leaf's nonce.
     * @param bytes The leaf's content bytes.
     */
    fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash

    /**
     * Calculates the hash of a node.
     * @param depth Depth of the node.
     * @param left [SecureHash] of the left child of the node.
     * @param right [SecureHash] of the right child of the node.
     */
    fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash
}

/**
 * Special Digest provider for supporting size proofs.
 */
interface MerkleTreeHashDigestProviderWithSizeProofSupport : MerkleTreeHashDigestProvider {
    /**
     * Returns a size proof that reveals the number of leaves in the Merkle tree, but not the content of the leaves.
     * @param leaves The tree's leaves.
     */
    fun getSizeProof(leaves: List<ByteArray>): MerkleProof
}