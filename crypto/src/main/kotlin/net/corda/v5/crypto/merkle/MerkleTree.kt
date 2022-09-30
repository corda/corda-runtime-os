package net.corda.v5.crypto.merkle

import net.corda.v5.crypto.SecureHash

/**
 * [MerkleTree]s are cryptographic data structures which can be used to create a short fingerprint of a larger
 * structured dataset.
 * From [MerkleTree]s, we can create [MerkleProof]s which let us prove that some particular data is part of the whole
 * tree without revealing the remaining data.
 *
 * @property leaves The input data elements. Usually something deterministically serialized.
 * @property digest The [MerkleTreeHashDigest] used to construct the tree's node and leaf hashes.
 * @property root The root element of the tree which is essentially the fingerprint of the whole tree/data set.
 *
 */
interface MerkleTree {
    val leaves: List<ByteArray>
    val digest: MerkleTreeHashDigest
    val root: SecureHash

    /**
     * Creates a [MerkleProof] for a set of leaves.
     * @param leafIndices whose leaf's inclusion is to be proven by the proof.
     *
     * @return [MerkleProof] for the input leaves.
     */
    fun createAuditProof(leafIndices: List<Int>): MerkleProof
}
