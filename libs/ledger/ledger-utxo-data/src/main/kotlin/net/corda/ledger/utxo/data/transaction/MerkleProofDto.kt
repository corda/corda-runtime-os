package net.corda.ledger.utxo.data.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * A representation of a Merkle proof.
 *
 * Contains the following fields:
 * - The transaction ID the Merkle proof was created from
 * - The component group index the Merkle proof belongs to
 * - Size of the original Merkle tree the proof was created from
 * - List of the revealed hashes
 * - Revealed leaf indices and the data associated with them
 * - An optional privacy salt from the transaction, used to calculate nonce
 * - Visible leaf indices
 */
@CordaSerializable
data class MerkleProofDto(
    val transactionId: String,
    val groupIndex: Int,
    val treeSize: Int,
    val hashes: List<SecureHash>,
    val privacySalt: ByteArray?,
    val leavesWithData: Map<Int, ByteArray>,
    // This is needed for top level proofs when we don't store the data, only the leaf indices
    val visibleLeaves: List<Int>
)

fun MerkleProofDto.toMerkleProof(
    merkleProofFactory: MerkleProofFactory,
    hashDigestProvider: MerkleTreeHashDigestProvider
): MerkleProof {
    return merkleProofFactory.createAuditMerkleProof(
        treeSize,
        leavesWithData,
        hashes,
        hashDigestProvider
    )
}
