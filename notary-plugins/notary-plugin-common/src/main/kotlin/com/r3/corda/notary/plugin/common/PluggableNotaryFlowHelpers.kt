@file:JvmName("PluggableNotaryFlowHelpers")

package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorNotPreviouslySeenTransaction
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowBeforeLowerBound
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.notary.plugin.core.NotaryException

/**
 * A helper function that will convert a [UniquenessCheckResult] to a [NotarizationResponse].
 */
@Suspendable
fun UniquenessCheckResult.toNotarizationResponse(
    txId: SecureHash?,
    signature: DigitalSignatureAndMetadata?
): NotarizationResponse {
    return when (val uniquenessResult = this) {
        is UniquenessCheckResultSuccess -> {
            require(signature != null) {
                "If the uniqueness check result was successful, a signature must be provided!"
            }
            NotarizationResponse(
                listOf(signature),
                null
            )
        }
        is UniquenessCheckResultFailure -> NotarizationResponse(
            emptyList(),
            uniquenessResult.error.toNotaryException(txId)
        )
        else -> NotarizationResponse(
            emptyList(),
            NotaryExceptionGeneral("Unknown uniqueness check result: $uniquenessResult")
        )
    }
}

/**
 * A helper function that will convert a [UniquenessCheckError] to a [NotaryException].
 */
@Suspendable
private fun UniquenessCheckError.toNotaryException(txId: SecureHash?): NotaryException {
    return when (this) {
        is UniquenessCheckErrorInputStateConflict -> NotaryExceptionInputStateConflict(conflictingStates, txId)
        is UniquenessCheckErrorInputStateUnknown -> NotaryExceptionInputStateUnknown(unknownStates, txId)
        is UniquenessCheckErrorReferenceStateConflict -> NotaryExceptionReferenceStateConflict(conflictingStates, txId)
        is UniquenessCheckErrorReferenceStateUnknown -> NotaryExceptionReferenceStateUnknown(unknownStates, txId)
        is UniquenessCheckErrorTimeWindowOutOfBounds -> NotaryExceptionTimeWindowOutOfBounds(
            evaluationTimestamp,
            timeWindowLowerBound,
            timeWindowUpperBound,
            txId
        )
        is UniquenessCheckErrorTimeWindowBeforeLowerBound -> NotaryExceptionTimeWindowBeforeLowerBound(
            evaluationTimestamp,
            timeWindowLowerBound
        )
        is UniquenessCheckErrorMalformedRequest -> NotaryExceptionMalformedRequest(errorText, txId)
        is UniquenessCheckErrorNotPreviouslySeenTransaction -> NotaryExceptionNotPreviouslySeenTransaction()
        is UniquenessCheckErrorUnhandledException -> NotaryExceptionGeneral(
            "Unhandled exception of type $unhandledExceptionType encountered during uniqueness checking with " +
                    "message: $unhandledExceptionMessage",
            txId
        )
        else -> NotaryExceptionGeneral(
            "Unknown error type received from uniqueness checker: ${this::class.java.canonicalName}",
            txId
        )
    }
}

/**
 * Error type used for scenarios that were unexpected, or couldn't be mapped.
 *
 * @property errorText Any additional details regarding the error
 */
@CordaSerializable
internal class NotaryExceptionGeneral(
    val errorText: String?,
    txId: SecureHash? = null
) : net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral(
    "General Error: $errorText",
    txId
)
