@file:JvmName("PluggableNotaryFlowHelpers")

package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigestService
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
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.notary.plugin.core.NotaryException
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

/**
 * Verifies that the correct notarization request was signed by the counterparty.
 *
 * @throws IllegalStateException if the request signature could not be validated.
 */
@Suspendable
@Suppress("LongParameterList")
fun validateRequestSignature(notarizationRequest: NotarizationRequest,
                             requestingPartyKey: PublicKey,
                             serializationService: SerializationService,
                             signatureVerifier: DigitalSignatureVerificationService,
                             signature: NotarizationRequestSignature,
                             digestService: DigestService
) {
    val digitalSignature = signature.digitalSignature
    val digestAlgorithmOfSignatureKeyId = digitalSignature.by.algorithm
    val requestingPartyKeyId =
        requestingPartyKey.fullIdHash(digestService, digestAlgorithmOfSignatureKeyId)
    if (requestingPartyKeyId != digitalSignature.by) {
        throw IllegalStateException(
            "Expected a signature by ${requestingPartyKeyId.shortHash}, " +
                    "but received by ${digitalSignature.by.shortHash}}"
        )
    }

    val expectedSignedBytes = serializationService.serialize(notarizationRequest).bytes

    try {
        signatureVerifier.verify(
            expectedSignedBytes,
            digitalSignature.bytes,
            requestingPartyKey,
            SignatureSpec.ECDSA_SHA256 // TODO This shouldn't be hardcoded?
        )
    } catch (e: Exception) {
        throw IllegalStateException("Error while verifying request signature.", e)
    }
}

/** Creates a signature over the notarization request using the legal identity key. */
@Suspendable
fun generateRequestSignature(notarizationRequest: NotarizationRequest,
                             memberInfo: MemberInfo,
                             serializationService: SerializationService,
                             signingService: SigningService
): NotarizationRequestSignature {
    val serializedRequest = serializationService.serialize(notarizationRequest).bytes
    // CORE-11837: Use notary key instead
    val myLegalIdentity = memberInfo.sessionInitiationKeys.first()
    val signature = signingService.sign(
        serializedRequest,
        myLegalIdentity,
        SignatureSpec.ECDSA_SHA256 // TODO This shouldn't be hardcoded?
    )
    return NotarizationRequestSignature(signature, memberInfo.platformVersion)
}

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
        is UniquenessCheckErrorMalformedRequest -> NotaryExceptionMalformedRequest(errorText, txId)
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

private const val SHORT_KEY_ID_LENGTH = 12

private fun PublicKey.fullIdHash(digestService: DigestService, digestAlgorithmName: String): SecureHash =
    digestService.hash(this.encoded, DigestAlgorithmName(digestAlgorithmName))

private val SecureHash.shortHash: String
    get() = this.toHexString().substring(0, SHORT_KEY_ID_LENGTH)
