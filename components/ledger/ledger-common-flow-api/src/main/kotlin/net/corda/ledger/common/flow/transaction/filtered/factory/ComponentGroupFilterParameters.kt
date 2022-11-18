package net.corda.ledger.common.flow.transaction.filtered.factory

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * [ComponentGroupFilterParameters] is used with [FilteredTransactionFactory] to specify what component groups include in the
 * [FilteredTransaction] that the factory creates.
 *
 * @see FilteredTransactionFactory
 */
sealed interface ComponentGroupFilterParameters {

    /**
     * Gets the index of the component group to include.
     */
    val componentGroupIndex: Int

    /**
     * Gets the type of [MerkleProof] to create.
     */
    val merkleProofType: MerkleProofType

    /**
     * [AuditProof] includes a component group in the [FilteredTransaction] and creates an audit proof from the filtered components.
     *
     * @property componentGroupIndex The index of the component group to include.
     * @property deserializedClass The type that the component group deserializes its components into.
     */
    data class AuditProof(override val componentGroupIndex: Int, val deserializedClass: Class<*>) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.AUDIT
    }

    /**
     * [SizeProof] includes a component group in the [FilteredTransaction] and creates a size proof from the component group.
     *
     * @property componentGroupIndex The index of the component group to include.
     */
    data class SizeProof(override val componentGroupIndex: Int) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.SIZE
    }
}