package net.corda.ledger.common.data.transaction.filtered

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class MerkleProofType {

    AUDIT, SIZE
}