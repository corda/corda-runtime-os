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
 */
@CordaSerializable
interface MerkleProof {
    /**
     * @property treeSize Number of leaves in the whole tree
     */
    val treeSize: Int

    /**
     * @property leaves Leaf items whose inclusion is proved by the proof.
     */
    val leaves: List<IndexedMerkleLeaf>

    /**
     * @property hashes The helper hashes needed to reconstruct the whole tree.
     */
    val hashes: List<SecureHash>

    /**
     * [verify] can be used to check if the [MerkleProof] has been generated from a [MerkleTree] with the given [root].
     */
    fun verify(
        root: SecureHash,
        digestProvider: MerkleTreeHashDigestProvider
    ): Boolean
}