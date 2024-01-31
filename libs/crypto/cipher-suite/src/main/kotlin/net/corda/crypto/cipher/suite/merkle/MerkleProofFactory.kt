package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * A service used to instantiate [MerkleProof]s.
 */
@DoNotImplement
interface MerkleProofFactory {

    /**
     * Create a new [MerkleProof] instance using the given data.
     *
     * @param treeSize Size of the original Merkle tree
     * @param leavesIndexAndData Visible leaf indices and their data
     * @param hashes List of the visible hashes in the Merkle proof
     * @param hashDigestProvider Hash digest provider used for nonces
     *
     * @return The constructed Merkle proof
     */
    fun createAuditMerkleProof(
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>,
        hashDigestProvider: MerkleTreeHashDigestProvider
    ): MerkleProof

}
