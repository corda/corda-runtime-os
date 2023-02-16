package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.core.NotaryError
import net.corda.v5.membership.MemberInfo

/**
 * Verifies that the correct notarisation request was signed by the counterparty.
 *
 * @throws IllegalStateException if the request signature could not be validated.
 */
@Suspendable
fun validateRequestSignature(notarisationRequest: NotarisationRequest,
                             requestingParty: Party,
                             serializationService: SerializationService,
                             signatureVerifier: DigitalSignatureVerificationService,
                             signature: NotarisationRequestSignature
) {
    val digitalSignature = signature.digitalSignature

    if (requestingParty.owningKey != digitalSignature.by) {
        throw IllegalStateException(
            "Expected a signature by ${requestingParty.owningKey.publicKeyId()}, " +
                    "but received by ${digitalSignature.by.publicKeyId()}}"
        )
    }

    val expectedSignedBytes = serializationService.serialize(notarisationRequest).bytes

    try {
        signatureVerifier.verify(
            digitalSignature.by,
            SignatureSpec.ECDSA_SHA256, // TODO This shouldn't be hardcoded?
            digitalSignature.bytes,
            expectedSignedBytes
        )
    } catch (e: Exception) {
        throw IllegalStateException("Error while verifying request signature.", e)
    }
}

/** Creates a signature over the notarisation request using the legal identity key. */
@Suspendable
fun generateRequestSignature(notarisationRequest: NotarisationRequest,
                             memberInfo: MemberInfo,
                             serializationService: SerializationService,
                             signingService: SigningService
): NotarisationRequestSignature {
    val serializedRequest = serializationService.serialize(notarisationRequest).bytes
    val myLegalIdentity = memberInfo.sessionInitiationKey
    val signature = signingService.sign(
        serializedRequest,
        myLegalIdentity,
        SignatureSpec.ECDSA_SHA256 // TODO This shouldn't be hardcoded?
    )
    return NotarisationRequestSignature(signature, memberInfo.platformVersion)
}

/**
 * A helper function that will convert a [UniquenessCheckResponse] to a [NotarisationResponse].
 */
@Suspendable
fun UniquenessCheckResult.toNotarisationResponse(signature: DigitalSignatureAndMetadata?): NotarisationResponse {
    return when (val uniquenessResult = this) {
        is UniquenessCheckResultSuccess -> {
            require(signature != null) {
                "If the uniqueness check result was successful, a signature must be provided!"
            }
            NotarisationResponse(
                listOf(signature),
                null
            )
        }
        is UniquenessCheckResultFailure -> NotarisationResponse(
            emptyList(),
            uniquenessResult.error.toNotaryError()
        )
        else -> NotarisationResponse(
            emptyList(),
            NotaryErrorGeneralImpl(
                "Unknown uniqueness check result: $uniquenessResult"
            )
        )
    }
}

/**
 * A helper function that will convert a [UniquenessCheckError] to a [NotaryError].
 */
@Suspendable
private fun UniquenessCheckError.toNotaryError(): NotaryError {
    return when (this) {
        is UniquenessCheckErrorInputStateConflict -> NotaryErrorInputStateConflictImpl(conflictingStates)
        is UniquenessCheckErrorInputStateUnknown -> NotaryErrorInputStateUnknownImpl(unknownStates)
        is UniquenessCheckErrorReferenceStateConflict -> NotaryErrorReferenceStateConflictImpl(conflictingStates)
        is UniquenessCheckErrorReferenceStateUnknown -> NotaryErrorReferenceStateUnknownImpl(unknownStates)
        is UniquenessCheckErrorTimeWindowOutOfBounds -> NotaryErrorTimeWindowOutOfBoundsImpl(
            evaluationTimestamp,
            timeWindowLowerBound,
            timeWindowUpperBound
        )
        is UniquenessCheckErrorMalformedRequest -> NotaryErrorMalformedRequestImpl(errorText)
        is UniquenessCheckErrorUnhandledException -> NotaryErrorGeneralImpl(unhandledExceptionMessage)
        else -> NotaryErrorGeneralImpl(
            "Unknown error type received from uniqueness checker: ${this::class.java.canonicalName}"
        )
    }
}
