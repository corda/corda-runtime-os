package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
data class MerkleProofDtoId(
    val transactionId: String,
    val groupId: Int,
    val leaves: List<Int>
)

@CordaSerializable
data class MerkleProofDto(
    val merkleProofDtoId: MerkleProofDtoId,
    val treeSize: Int,
    val leavesWithData: Map<Int, ByteArray>,
    val hashes: List<SecureHash>
)