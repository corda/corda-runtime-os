package com.r3.corda.notary.plugin.common

import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.toBase58
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.pluggable.NotarisationRequestSignature
import net.corda.v5.ledger.utxo.StateRef
import org.slf4j.Logger
import java.security.PublicKey

/** Return the Base58 representation of the serialised public key. */
fun PublicKey.toBase58String(): String = this.encoded.toBase58()

/** Verifies that the correct notarisation request was signed by the counterparty. */
fun validateRequestSignature(requestingParty: Party,
                             serializationService: SerializationService,
                             signatureVerifier: DigitalSignatureVerificationService,
                             request: NotarisationRequestImpl,
                             signature: NotarisationRequestSignature
) {
    request.verifySignature(signature, requestingParty, serializationService, signatureVerifier)
}

/**
 * A simple helper function that constructs a [NotarisationResponseImpl] and sends it back to the client
 * ([otherSideSession]).
 */
@Suspendable
fun sendUniquenessServiceCommitStatus(logger: Logger,
                                      otherSideSession: FlowSession,
                                      txId: SecureHash?,
                                      uniquenessCheckResponse: UniquenessCheckResponse) {
    val clientResponse = when (val uniquenessResult = uniquenessCheckResponse.result) {
        is UniquenessCheckResultSuccess -> {
            logger.info("Transaction [$txId] successfully notarised, sending response back to [${otherSideSession.counterparty}]")
            NotarisationResponseImpl(
                listOf(uniquenessCheckResponse.signature!!), // TODO This should never fail but maybe add better error handling
                null
            )
        }
        is UniquenessCheckResultFailure -> {
            logger.warn("Transaction [$txId] could not be notarised, reason: ${uniquenessResult.error}" +
                    " sending error back to [${otherSideSession.counterparty}]")
            NotarisationResponseImpl(emptyList(), uniquenessResult.error)
        }
        else -> {
            logger.error("Received an unknown result type from the uniqueness checker, this should never happen.")
            throw IllegalStateException("Received an unknown result type from the uniqueness checker, this should never happen.")
        }
    }

    otherSideSession.send(clientResponse)
}
