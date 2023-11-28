package net.corda.uniqueness.datamodel.internal

import net.corda.crypto.core.parseSecureHash
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.uniqueness.datamodel.common.toStateRef
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * Internal representation of a uniqueness check request, used by the uniqueness checker and
 * backing store only. This simply wraps the external message bus request, converting data that
 * is represented as primitive types into the internal types used within the uniqueness checker.
 */
data class UniquenessCheckRequestInternal constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val originatorX500Name: String,
    val inputStates: List<UniquenessCheckStateRef>,
    val referenceStates: List<UniquenessCheckStateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
) {
    companion object {
        fun create(externalRequest: UniquenessCheckRequestAvro): UniquenessCheckRequestInternal {
            with(externalRequest) {
                require(numOutputStates >= 0) { "Number of output states cannot be less than 0." }

                val duplicateInputs = inputStates.groupingBy { it }.eachCount().filter { it.value > 1 }

                require(duplicateInputs.isEmpty()) { "Duplicate input states detected: ${duplicateInputs.keys}" }

                val duplicateReferences = referenceStates.groupingBy { it }.eachCount().filter { it.value > 1 }

                require(duplicateReferences.isEmpty()) { "Duplicate reference states detected: ${duplicateReferences.keys}" }

                val intersection = inputStates intersect referenceStates.toSet()

                require(intersection.isEmpty()) {
                    "A state cannot be both an input and a reference input in the same request. Offending " +
                        "states: $intersection"
                }

                return UniquenessCheckRequestInternal(
                    parseSecureHash(txId),
                    txId,
                    originatorX500Name,
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
