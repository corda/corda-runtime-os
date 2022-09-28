package net.corda.ledger.common.flow.transaction.factory

import net.corda.ledger.common.data.transaction.filtered.MerkleProofType

sealed interface ComponentGroupFilterParameters {

    val componentGroupOrdinal: Int
    val merkleProofType: MerkleProofType

    data class AuditProof(override val componentGroupOrdinal: Int, val deserializedClass: Class<*>) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.AUDIT
    }

    data class SizeProof(override val componentGroupOrdinal: Int) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.SIZE
    }
}