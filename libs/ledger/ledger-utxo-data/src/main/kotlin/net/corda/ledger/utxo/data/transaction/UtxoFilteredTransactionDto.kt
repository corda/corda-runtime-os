package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class UtxoFilteredTransactionDto(
    val transactionId: String,
    val topLevelMerkleProofs: List<MerkleProofDto>,
    val componentMerkleProofMap: Map<Int, List<MerkleProofDto>>,
    val privacySalt: PrivacySalt?,
    val metadataBytes: ByteArray?,
    val signatures: List<DigitalSignatureAndMetadata>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoFilteredTransactionDto

        if (transactionId != other.transactionId) return false
        if (topLevelMerkleProofs != other.topLevelMerkleProofs) return false
        if (componentMerkleProofMap != other.componentMerkleProofMap) return false
        if (privacySalt != other.privacySalt) return false
        if (metadataBytes != null) {
            if (other.metadataBytes == null) return false
            if (!metadataBytes.contentEquals(other.metadataBytes)) return false
        } else if (other.metadataBytes != null) return false
        if (signatures != other.signatures) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.hashCode()
        result = 31 * result + topLevelMerkleProofs.hashCode()
        result = 31 * result + componentMerkleProofMap.hashCode()
        result = 31 * result + (privacySalt?.hashCode() ?: 0)
        result = 31 * result + (metadataBytes?.contentHashCode() ?: 0)
        result = 31 * result + signatures.hashCode()
        return result
    }
}
