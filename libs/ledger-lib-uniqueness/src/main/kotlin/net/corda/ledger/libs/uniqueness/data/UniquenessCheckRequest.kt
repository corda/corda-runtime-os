package net.corda.ledger.libs.uniqueness.data

import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

@CordaSerializable
enum class UniquenessCheckType {
    WRITE, READ;
}

// We have raw strings here because this is an "external" API so we can't expect the caller to know our data types
data class UniquenessCheckRequest(
    val uniquenessCheckType: UniquenessCheckType,
    val transactionId: String,
    val initiator: String, // x500
    val inputStates: List<String>,
    val referenceStates: List<String>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant,
    val holdingIdentity: UniquenessHoldingIdentity,
    val additionalData: Map<String, Any> = emptyMap()
) {
    fun toInternal(): UniquenessCheckRequestInternal {

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
            parseSecureHash(transactionId),
            transactionId,
            initiator,
            inputStates.map { it.toStateRef() },
            referenceStates.map { it.toStateRef() },
            numOutputStates,
            timeWindowLowerBound,
            timeWindowUpperBound
        )
    }
}

fun String.toStateRef(): UniquenessCheckStateRef {
    return UniquenessCheckStateRefImpl(
        parseSecureHash(substringBeforeLast(":")),
        substringAfterLast(":").toInt()
    )
}
