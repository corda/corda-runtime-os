package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.toNotarisationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationRequest
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneralImpl
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
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
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import kotlin.IllegalStateException

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
// TODO CORE-7292 What is the best way to define the protocol
@InitiatedBy(protocol = "non-validating-notary")
class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

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
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.NotarisationResponse]
     * back to the client including the specific
     * [NotaryError][net.corda.v5.ledger.notary.plugin.core.NotaryError] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarisationPayload::class.java)

            val txDetails = validateRequest(requestPayload)
            val request = NotarisationRequest(txDetails.inputs, txDetails.id)

            val otherMemberInfo = memberLookup.lookup(session.counterparty)
                ?: throw IllegalStateException("Could not find counterparty on the network: ${session.counterparty}")

            val otherParty = Party(otherMemberInfo.name, otherMemberInfo.sessionInitiationKey)

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
                NotaryErrorGeneralImpl("Error while processing request from client.", e)
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
            throw IllegalStateException("Could not validate request.", e)
        }

        // TODO CORE-8976 Add check for notary identity

        return transactionParts
    }

    /**
     * A helper function that constructs an instance of [NonValidatingNotaryTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {
        val filteredTx = requestPayload.transaction as UtxoFilteredTransaction
        // The notary component is not needed by us but we validate that it is present just in case
        requireNotNull(filteredTx.notary) {
            "Notary component could not be found on the transaction"
        }

        requireNotNull(filteredTx.timeWindow) {
            "Time window component could not be found on the transaction"
        }

        val inputStates = filteredTx.inputStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch input states from the filtered transaction"
        }

        val refStates = filteredTx.referenceInputStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch reference input states from the filtered transaction"
        }

        val outputStates = filteredTx.outputStateAndRefs.castOrThrow<UtxoFilteredData.SizeOnly<StateAndRef<*>>> {
            "Could not fetch output states from the filtered transaction"
        }

        return NonValidatingNotaryTransactionDetails(
            filteredTx.id,
            outputStates.size,
            filteredTx.timeWindow!!,
            inputStates.values.values,
            refStates.values.values,
            filteredTx.notary!!
        )
    }

    /**
     * A non-validating plugin specific verification logic.
     *
     * @throws IllegalStateException if the transaction could not be verified.
     */
    @Suspendable
    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught", "ThrowsCount",)
    private fun verifyTransaction(requestPayload: NonValidatingNotarisationPayload) {
        try {
            (requestPayload.transaction as UtxoFilteredTransaction).verify()
        } catch (e: Exception) {
            logger.warn("Error while validating the transaction, reason: ${e.message}")
            throw IllegalStateException("Error while validating the transaction", e)
        }
    }

    private inline fun <reified T> Any.castOrThrow(error: () -> String) = this as? T
        ?: throw java.lang.IllegalStateException(error())
}
