package net.corda.uniqueness.datamodel.internal

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.uniqueness.datamodel.common.toStateRef
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * TODO Rewrite KDocs
 */
data class UniquenessCheckRequestInternal private constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val inputStates: List<UniquenessCheckStateRef>,
    val referenceStates: List<UniquenessCheckStateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
) {
    companion object {
        fun create(externalRequest: UniquenessCheckRequestAvro): UniquenessCheckRequestInternal {
            if (externalRequest.numOutputStates < 0) {
                throw IllegalArgumentException("Number of output states cannot be less than 0.")
            }

            with (externalRequest) {
                return UniquenessCheckRequestInternal(
                    SecureHash.parse(txId),
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
