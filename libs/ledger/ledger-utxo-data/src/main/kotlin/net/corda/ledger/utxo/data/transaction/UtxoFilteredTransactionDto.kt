package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class UtxoFilteredTransactionDto(
    val transactionId: String,
    val topLevelMerkleProofs: List<MerkleProofDto>,
    val componentMerkleProofMap: Map<Int, List<MerkleProofDto>>,
    val privacySalt: PrivacySalt,
    val metadataBytes: ByteArray,
    val signatures: List<DigitalSignatureAndMetadata>
)
