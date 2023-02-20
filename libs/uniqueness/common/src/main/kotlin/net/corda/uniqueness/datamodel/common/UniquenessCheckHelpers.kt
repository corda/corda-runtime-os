package net.corda.uniqueness.datamodel.common

import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultInputStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultMalformedRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateConflictAvro
import net.corda.data.uniqueness.UniquenessCheckResultReferenceStateUnknownAvro
import net.corda.data.uniqueness.UniquenessCheckResultSuccessAvro
import net.corda.data.uniqueness.UniquenessCheckResultTimeWindowOutOfBoundsAvro
import net.corda.data.uniqueness.UniquenessCheckResultUnhandledExceptionAvro
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import org.apache.avro.specific.SpecificRecord
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.time.Instant

/**
 * Converts an Avro result to a [UniquenessCheckResult].
 */
fun UniquenessCheckResponseAvro.toUniquenessResult(): UniquenessCheckResult {

    return when (val avroResult = result) {
        is UniquenessCheckResultInputStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultInputStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorInputStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultReferenceStateConflictAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateConflictImpl(avroResult.conflictingStates.map {
                    // FIXME Consuming tx hash is populated as [null] for now
                    UniquenessCheckStateDetailsImpl(it.toStateRef(), null)
                })
            )
        }
        is UniquenessCheckResultReferenceStateUnknownAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateUnknownImpl(avroResult.unknownStates.map {
                    it.toStateRef()
                })
            )
        }
        is UniquenessCheckResultTimeWindowOutOfBoundsAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                    avroResult.evaluationTimestamp,
                    avroResult.timeWindowLowerBound,
                    avroResult.timeWindowUpperBound
                )
            )
        }
        is UniquenessCheckResultMalformedRequestAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorMalformedRequestImpl(
                    avroResult.errorText
                )
            )
        }
        is UniquenessCheckResultUnhandledExceptionAvro -> {
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorUnhandledExceptionImpl(
                    avroResult.exception.errorType,
                    avroResult.exception.errorMessage
                )
            )
        }
        is UniquenessCheckResultSuccessAvro -> {
            UniquenessCheckResultSuccessImpl(avroResult.commitTimestamp)
        }
        else -> {
            throw IllegalArgumentException(
                "Unable to convert Avro type \"${avroResult.javaClass.typeName}\" to result")
        }
    }
}

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
                is UniquenessCheckErrorInputStateConflict ->
                    UniquenessCheckResultInputStateConflictAvro(
                        uniquenessError.conflictingStates.map { it.stateRef.toString() }
                    )

                is UniquenessCheckErrorInputStateUnknown ->
                    UniquenessCheckResultInputStateUnknownAvro(
                        uniquenessError.unknownStates.map { it.toString() }
                    )

                is UniquenessCheckErrorReferenceStateConflict ->
                    UniquenessCheckResultReferenceStateConflictAvro(
                        uniquenessError.conflictingStates.map { it.stateRef.toString() }
                    )

                is UniquenessCheckErrorReferenceStateUnknown ->
                    UniquenessCheckResultReferenceStateUnknownAvro(
                        uniquenessError.unknownStates.map { it.toString() }
                    )

                is UniquenessCheckErrorTimeWindowOutOfBounds ->
                    UniquenessCheckResultTimeWindowOutOfBoundsAvro(
                        uniquenessError.evaluationTimestamp,
                        uniquenessError.timeWindowLowerBound,
                        uniquenessError.timeWindowUpperBound
                    )

                else -> {
                    throw IllegalArgumentException(
                        "Unable to convert result type \"${uniquenessError.javaClass.typeName}\" to Avro"
                    )
                }
            }
        }
        else -> {
            throw IllegalStateException(
                "Unknown result type: ${this.javaClass.typeName}"
            )
        }
    }
}

fun UniquenessCheckResult.toCharacterRepresentation() = if (this is UniquenessCheckResultSuccess) {
    UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
} else {
    UniquenessConstants.RESULT_REJECTED_REPRESENTATION
}

fun String.toStateRef() : UniquenessCheckStateRef {
    return UniquenessCheckStateRefImpl(
        SecureHash.parse(substringBeforeLast(":")),
        substringAfterLast(":").toInt()
    )
}
