package net.corda.v5.crypto.merkle

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * [MerkleProof]s can be used to verify if some specific data is part of a [MerkleTree].
 *
 * Use [net.corda.v5.crypto.merkle.MerkleTree.createAuditProof] to create a proof for a set of leaves for an
 * existing [MerkleTree].
 * Construct a [MerkleProof] from its ([treeSize], [leaves], [hashes]) when you want to [verify] if the leaves
 * to be checked are part of a [MerkleTree] with the specific root.
 *
 * @property treeSize The total number of leaves in the tree.
 * @property leaves The leaf items whose inclusion is proved by the proof.
 * @property hashes The helper hashes needed to reconstruct the whole tree.
 *
 */
@CordaSerializable
interface MerkleProof {
    val treeSize: Int
    val leaves: List<IndexedMerkleLeaf>
    val hashes: List<SecureHash>

    /**
     * Checks if the [MerkleProof] has been generated from a [MerkleTree] with the given [root].
     * @param root The root of the tree to be verified.
     * @param digest The tree's digest.
     *
     * @returns Result of the verification.
     */
    fun verify(
        root: SecureHash,
        digest: MerkleTreeHashDigest
    ): Boolean
}
