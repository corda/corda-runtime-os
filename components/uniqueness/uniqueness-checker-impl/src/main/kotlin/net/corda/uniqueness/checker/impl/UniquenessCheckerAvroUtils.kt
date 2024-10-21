package net.corda.uniqueness.checker.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultNotPreviouslySeenTransactionAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowBeforeLowerBoundAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowOutOfBoundsAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.data.uniqueness.UniquenessCheckType
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckRequest
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckResponse
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorNotPreviouslySeenTransaction
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowBeforeLowerBound
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.virtualnode.toCorda
import org.apache.avro.specific.SpecificRecord

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

    /**
     * Converts a [UniquenessCheckResult] to an Avro result.
     */
    fun UniquenessCheckResult.toAvro(): SpecificRecord {
        return when (this) {
            is UniquenessCheckResultSuccess -> {
                UniquenessCheckResultSuccessAvro(this.resultTimestamp)
            }
            is UniquenessCheckResultFailure -> {
                when (val uniquenessError = this.error) {
                    is UniquenessCheckErrorInputStateConflict -> {
                        UniquenessCheckResultInputStateConflictAvro(
                            uniquenessError.conflictingStates.map { it.stateRef.toString() }
                        )
                    }
                    is UniquenessCheckErrorInputStateUnknown -> {
                        UniquenessCheckResultInputStateUnknownAvro(
                            uniquenessError.unknownStates.map { it.toString() }
                        )
                    }
                    is UniquenessCheckErrorReferenceStateConflict -> {
                        UniquenessCheckResultReferenceStateConflictAvro(
                            uniquenessError.conflictingStates.map { it.stateRef.toString() }
                        )
                    }
                    is UniquenessCheckErrorReferenceStateUnknown -> {
                        UniquenessCheckResultReferenceStateUnknownAvro(
                            uniquenessError.unknownStates.map { it.toString() }
                        )
                    }
                    is UniquenessCheckErrorTimeWindowOutOfBounds -> {
                        UniquenessCheckResultTimeWindowOutOfBoundsAvro(
                            uniquenessError.evaluationTimestamp,
                            uniquenessError.timeWindowLowerBound,
                            uniquenessError.timeWindowUpperBound
                        )
                    }
                    is UniquenessCheckErrorTimeWindowBeforeLowerBound -> {
                        UniquenessCheckResultTimeWindowBeforeLowerBoundAvro(
                            uniquenessError.evaluationTimestamp,
                            uniquenessError.timeWindowLowerBound
                        )
                    }
                    is UniquenessCheckErrorNotPreviouslySeenTransaction -> {
                        UniquenessCheckResultNotPreviouslySeenTransactionAvro()
                    }
                    is UniquenessCheckErrorUnhandledException -> {
                        UniquenessCheckResultUnhandledExceptionAvro(
                            ExceptionEnvelope(
                                uniquenessError.unhandledExceptionType,
                                uniquenessError.unhandledExceptionMessage
                            )
                        )
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "Unable to convert result type \"${uniquenessError.javaClass.typeName}\" to Avro"
                        )
                    }
                }
            }
            else -> throw IllegalStateException("Unknown result type: ${this.javaClass.typeName}")
        }
    }
}
