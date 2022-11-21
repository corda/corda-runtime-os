package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.toNotarisationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationRequest
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneralImpl
import com.r3.corda.notary.plugin.nonvalidating.api.INPUTS_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.NOTARY_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import com.r3.corda.notary.plugin.nonvalidating.api.OUTPUTS_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.REFERENCES_GROUP
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import java.lang.IllegalStateException

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
// TODO CORE-7292 What is the best way to define the protocol
// TODO CORE-7249 Currently we need to `spy` this flow because some of the logic is missing and we need to "mock" it.
//  Mockito needs the class the be open to spy it. We need to remove `open` qualifier when we have an actual logic.
@InitiatedBy(protocol = "non-validating-notary")
open class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    private companion object {
        val logger: Logger = loggerFor<NonValidatingNotaryServerFlowImpl>()
    }

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        serializationService: SerializationService,
        signatureVerifier: DigitalSignatureVerificationService,
        memberLookup: MemberLookup
    ) : this() {
        this.clientService = clientService
        this.serializationService = serializationService
        this.signatureVerifier = signatureVerifier
        this.memberLookup = memberLookup
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Request uniqueness checking using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.response.NotarisationResponse]
     * back to the client including the specific
     * [NotaryError][net.corda.v5.ledger.notary.plugin.core.NotaryError] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarisationPayload::class.java)

            val txDetails = validateRequest(requestPayload)
            val request = NotarisationRequest(txDetails.inputs, txDetails.id)

            // TODO This shouldn't ever fail but should add an error handling
            // TODO Discuss this with MGM team but should we able to look up members by X500 name?
            val otherMemberInfo = memberLookup.lookup(session.counterparty)!!
            val otherParty = Party(session.counterparty, otherMemberInfo.sessionInitiationKey)

            validateRequestSignature(
                request,
                otherParty,
                serializationService,
                signatureVerifier,
                requestPayload.requestSignature
            )

            verifyTransaction(requestPayload)

            val uniquenessResponse = clientService.requestUniquenessCheck(
                txDetails.id.toString(),
                txDetails.inputs.map { it.toString() },
                txDetails.references.map { it.toString() },
                txDetails.numOutputs,
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )

            logger.debug {
                "Uniqueness check completed for transaction with Tx [${txDetails.id}], " +
                        "result is: ${uniquenessResponse.result}"
            }

            session.send(uniquenessResponse.toNotarisationResponse())
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e")
            session.send(NotarisationResponse(
                emptyList(),
                NotaryErrorGeneralImpl("Error while processing request from client. Reason: ${e.message}", e)
            ))
        }
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * @throws IllegalStateException if the request could not be validated.
     */
    @Suspendable
    @Suppress("TooGenericExceptionCaught")
    private fun validateRequest(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {

        val transactionParts = try {
            extractParts(requestPayload)
        } catch (e: Exception) {
            logger.warn("Could not validate request. Reason: ${e.message}")
            throw IllegalStateException("Could not validate request. Reason: ${e.message}")
        }

        return transactionParts
    }

    /**
     * A helper function that constructs an instance of [NonValidatingNotaryTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {
        // Notary component group will contain notary's identity as the first element and second as the time window
        val timeWindowBytes = requestPayload.transaction.getComponentGroupContent(NOTARY_GROUP)?.get(1)?.second
            ?: throw IllegalStateException("Time window component not found on transaction")

        val inputsBytes = requestPayload.transaction.getComponentGroupContent(INPUTS_GROUP)
            ?: throw IllegalStateException("Input states component not found on transaction")

        val refsBytes = requestPayload.transaction.getComponentGroupContent(REFERENCES_GROUP)
            ?: throw IllegalStateException("Reference states component not found on transaction")

        val outputCount = requestPayload.transaction.getComponentGroupContent(OUTPUTS_GROUP)?.size
            ?: throw IllegalStateException("Output states component not found on transaction")

        val timeWindow = serializationService.deserialize(
            timeWindowBytes,
            TimeWindow::class.java
        )

        val inputs = inputsBytes.map {
            serializationService.deserialize(it.second, StateAndRef::class.java).ref
        }

        val refs = refsBytes.map {
            serializationService.deserialize(it.second, StateAndRef::class.java).ref
        }

        return NonValidatingNotaryTransactionDetails(
            requestPayload.transaction.id,
            outputCount,
            timeWindow,
            inputs,
            refs
        )
    }

    /**
     * A non-validating plugin specific verification logic.
     *
     * @throws IllegalStateException if the transaction could not be verified.
     */
    @Suspendable
    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "ThrowsCount",)
    // TODO CORE-7249 Remove `open` qualifier when we have an actual logic. Mockito needs this function to be open in
    //  order to be mockable (via spy).
    private fun verifyTransaction(requestPayload: NonValidatingNotarisationPayload) {
        val transaction = requestPayload.transaction
        try {
            transaction.verify()
            // TODO checkAllComponentsVisible is not available anymore, do we need that or those are implicitly
            //  included in verify
        } catch (e: Exception) {
            logger.warn("Error while validating the transaction, reason: ${e.message}")
            throw IllegalStateException("Error while validating the transaction, reason: ${e.message}")
        }
    }
}
