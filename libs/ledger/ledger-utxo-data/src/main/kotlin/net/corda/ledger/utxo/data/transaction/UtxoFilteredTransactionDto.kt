package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class UtxoFilteredTransactionDto(
    val transactionId: String,
    val merkleProofMap: Map<Int, List<MerkleProofDto>>,
    val privacySalt: PrivacySalt,
    val metadataBytes: ByteArray
)
