package net.corda.v5.crypto.merkle

import net.corda.v5.crypto.SecureHash

interface MerkleTree {
    val leaves: List<ByteArray>
    val digestProvider: MerkleTreeHashDigestProvider
    val root: SecureHash
    fun createAuditProof(leafIndices: List<Int>): MerkleProof
}