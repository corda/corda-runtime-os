package net.corda.ledger.common.data.transaction.filtered

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * [MerkleProofType] represents what type of [MerkleProof] was created.
 */
@CordaSerializable
enum class MerkleProofType {

    AUDIT, SIZE
}