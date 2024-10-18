package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckType
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckRequest
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckResponse
import net.corda.uniqueness.datamodel.common.toAvro
import net.corda.virtualnode.toCorda

object UniquenessCheckerAvroUtils {
    fun UniquenessCheckRequestAvro.toCorda(): UniquenessCheckRequest {

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

        return UniquenessCheckRequest(
            uniquenessCheckType = uniquenessCheckType.toCorda(),
            transactionId = txId,
            initiator = originatorX500Name,
            inputStates = inputStates,
            referenceStates = referenceStates,
            numOutputStates = numOutputStates,
            timeWindowLowerBound = timeWindowLowerBound,
            timeWindowUpperBound = timeWindowUpperBound,
            holdingIdentity = holdingIdentity.toCorda()
        )
    }

    private fun UniquenessCheckType.toCorda() = net.corda.ledger.libs.uniqueness.data.UniquenessCheckType.valueOf(toString())

    fun UniquenessCheckResponse.toAvro() = UniquenessCheckResponseAvro(transactionId, uniquenessCheckResult.toAvro())
}
