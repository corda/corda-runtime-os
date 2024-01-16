package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * A service used to instantiate [MerkleProof]s.
 */
@DoNotImplement
interface MerkleProofFactory {

    /**
     * Create a new [MerkleProof] instance using the given data.
     *
     * @param transactionId Transaction ID the Merkle proof belongs to
     * @param groupId Component group index the Merkle proof belongs to
     * @param treeSize Size of the original Merkle tree
     * @param leavesIndexAndData Visible leaf indices and their data
     * @param hashes List of the visible hashes in the Merkle proof
     *
     * @return The constructed Merkle proof
     */
    @Suspendable
    fun createAuditMerkleProof(
        transactionId: String,
        groupId: Int,
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>
    ): MerkleProof

}
