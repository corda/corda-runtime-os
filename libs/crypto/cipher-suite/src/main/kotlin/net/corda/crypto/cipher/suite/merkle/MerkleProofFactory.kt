package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof

@DoNotImplement
interface MerkleProofFactory {

    // TODO Create a batch function?
    @Suspendable
    fun createMerkleProof(
        transactionId: String,
        groupId: Int,
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>
    ): MerkleProof
}