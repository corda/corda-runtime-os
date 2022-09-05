package net.corda.uniqueness.datamodel.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckRequest
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant

/**
 * TODO Rewrite KDocs
 */
data class UniquenessCheckRequestImpl private constructor(
    override val txId: SecureHash,
    override val rawTxId: String,
    override val inputStates: List<UniquenessCheckStateRef>,
    override val referenceStates: List<UniquenessCheckStateRef>,
    override val numOutputStates: Int,
    override val timeWindowLowerBound: Instant?,
    override val timeWindowUpperBound: Instant
) : UniquenessCheckRequest {
    companion object {
        fun create(externalRequest: UniquenessCheckRequestAvro): UniquenessCheckRequestImpl {
            if (externalRequest.numOutputStates < 0) {
                throw IllegalArgumentException("Number of output states cannot be less than 0.")
            }

            with (externalRequest) {
                return UniquenessCheckRequestImpl(
                    SecureHash.create(txId),
                    txId,
                    inputStates?.map { it.toStateRef() } ?: emptyList(),
                    referenceStates?.map { it.toStateRef() } ?: emptyList(),
                    numOutputStates,
                    timeWindowLowerBound,
                    timeWindowUpperBound
                )
            }
        }
    }
}
