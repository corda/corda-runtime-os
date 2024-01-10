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
