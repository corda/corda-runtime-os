package net.corda.ledger.common.data.transaction.filtered

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.merkle.MerkleProof

@CordaSerializable
data class FilteredComponentGroup(
    val componentGroupOrdinal: Int,
    val merkleProof: MerkleProof,
    val merkleProofType: MerkleProofType
)