package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest

interface MerkleProofInternal : MerkleProof {

    /**
     *
     * Derive a proof that merges this proof with another.
     *
     * @param other Another proof to consider, which must be for the same Merkle tree.
     * @param digest A hash digest provider, which must be compatible with this and the `other` proof.
     * @return A MerkelProofImpl which has the union of the leaves and the required proof hashes to be verifiable.
     */
    fun merge(other: MerkleProof, digest: MerkleTreeHashDigestProvider): MerkleProofInternal

    /**
     *
     * Work out a Merkle proof with specified leaves.
     *
     * Throws IllegalArgumentException if some requested leaf indices are not available in the source proof,
     * or if no leaf indices are requested.
     *
     * @param digest the digest algorithm of this Merkle proof.
     * @param leafIndices indices  of the known leaves to include in the output proof
     * @return A new Merkle proof covering the specified leaves.
     */
    fun subset(digest: MerkleTreeHashDigest, leafIndices: List<Int>): MerkleProofInternal

    /**
     * Render the Merkle proof into a [String] representation.
     *
     * @param digest The digest to use for rendering
     * @return The string representation of the Merkle proof
     */
    fun render(digest: MerkleTreeHashDigest): String
}
