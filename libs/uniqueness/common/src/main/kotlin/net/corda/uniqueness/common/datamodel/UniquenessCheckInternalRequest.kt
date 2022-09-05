package net.corda.uniqueness.common.datamodel

import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * Internal representation of a uniqueness check request, used by the uniqueness checker and
 * backing store only. This simply wraps the external message bus request, converting data that
 * is represented as primitive types into the internal types used within the uniqueness checker.
 */
data class UniquenessCheckInternalRequest private constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val inputStates: List<UniquenessCheckInternalStateRef>,
    val referenceStates: List<UniquenessCheckInternalStateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
) {
    companion object {
        fun create(externalRequest: UniquenessCheckRequest): UniquenessCheckInternalRequest {
            if (externalRequest.numOutputStates < 0) {
                throw IllegalArgumentException("Number of output states cannot be less than 0.")
            }

            with (externalRequest) {
                return UniquenessCheckInternalRequest(
                    SecureHash.parse(txId),
                    txId,
                    inputStates?.map { it.toInternalStateRef() } ?: emptyList(),
                    referenceStates?.map { it.toInternalStateRef() } ?: emptyList(),
                    numOutputStates,
                    timeWindowLowerBound,
                    timeWindowUpperBound
                )
            }
        }

        private fun String.toInternalStateRef(): UniquenessCheckInternalStateRef {
            return UniquenessCheckInternalStateRef(
                SecureHash.parse(this.substringBeforeLast(':')),
                this.substringAfterLast(':').toInt()
            )
        }
    }
}
