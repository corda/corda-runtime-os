package net.corda.ledger.libs.uniqueness.data

import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

// We have raw strings here because this is an "external" API so we can't expect the caller to know our data types
data class UniquenessCheckRequest(
    val uniquenessCheckRequestType: Type,
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
    @CordaSerializable
    enum class Type {
        WRITE, READ;
    }

    fun toInternal(uniquenessSecureHashFactory: UniquenessSecureHashFactory): UniquenessCheckRequestInternal {

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
            uniquenessSecureHashFactory.parseSecureHash(transactionId),
            transactionId,
            initiator,
            inputStates.map { it.toStateRef(uniquenessSecureHashFactory) },
            referenceStates.map { it.toStateRef(uniquenessSecureHashFactory) },
            numOutputStates,
            timeWindowLowerBound,
            timeWindowUpperBound
        )
    }
}

fun String.toStateRef(uniquenessSecureHashFactory: UniquenessSecureHashFactory): UniquenessCheckStateRef {
    return UniquenessCheckStateRefImpl(
        uniquenessSecureHashFactory.parseSecureHash(substringBeforeLast(":")),
        substringAfterLast(":").toInt()
    )
}
