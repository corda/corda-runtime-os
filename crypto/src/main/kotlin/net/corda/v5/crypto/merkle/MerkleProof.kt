package net.corda.v5.crypto.merkle

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
interface MerkleProof {

    val treeSize: Int
    val leaves: List<IndexedMerkleLeaf>
    val hashes: List<SecureHash>

    fun verify(
        root: SecureHash,
        digestProvider: MerkleTreeHashDigestProvider
    ): Boolean
}