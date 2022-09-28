package net.corda.ledger.common.data.transaction.filtered

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.merkle.MerkleProof

@CordaSerializable
data class FilteredComponentGroup(
    val componentGroupOrdinal: Int,
    val merkleProof: MerkleProof, // Compares to leaf of root merkle proof with the same component group index
    val merkleProofType: MerkleProofType
)