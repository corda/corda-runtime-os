package net.corda.uniqueness.common.datamodel

import net.corda.data.uniqueness.UniquenessCheckExternalRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * TODO Rewrite KDocs
 */
data class UniquenessCheckInternalRequest private constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val inputStates: List<UniquenessCheckStateRef>,
    val referenceStates: List<UniquenessCheckStateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
) {
    companion object {
        fun create(externalRequest: UniquenessCheckExternalRequest): UniquenessCheckInternalRequest {
            if (externalRequest.numOutputStates < 0) {
                throw IllegalArgumentException("Number of output states cannot be less than 0.")
            }

            with (externalRequest) {
                return UniquenessCheckInternalRequest(
                    SecureHash.create(txId),
                    txId,
                    inputStates?.map { it.toInternalStateRef() } ?: emptyList(),
                    referenceStates?.map { it.toInternalStateRef() } ?: emptyList(),
                    numOutputStates,
                    timeWindowLowerBound,
                    timeWindowUpperBound
                )
            }
        }

        private fun String.toInternalStateRef(): UniquenessCheckStateRef {
            return UniquenessCheckStateRef(
                SecureHash.create(this.substringBeforeLast(':')),
                this.substringAfterLast(':').toInt()
            )
        }
    }
}
