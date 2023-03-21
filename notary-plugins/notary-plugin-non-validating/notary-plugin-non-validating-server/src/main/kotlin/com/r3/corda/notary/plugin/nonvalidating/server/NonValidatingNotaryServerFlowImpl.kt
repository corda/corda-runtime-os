package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarizationRequest
import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.toNotarizationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarizationPayload
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
@InitiatedBy(protocol = "net.corda.notary.NonValidatingNotary")
class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    private lateinit var digestService: DigestService

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    @Suppress("LongParameterList")
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        serializationService: SerializationService,
        signatureVerifier: DigitalSignatureVerificationService,
        memberLookup: MemberLookup,
        transactionSignatureService: TransactionSignatureService,
        digestService: DigestService
    ) : this() {
        this.clientService = clientService
        this.serializationService = serializationService
        this.signatureVerifier = signatureVerifier
        this.memberLookup = memberLookup
        this.transactionSignatureService = transactionSignatureService
        this.digestService = digestService
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Request uniqueness checking using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.NotarizationResponse]
     * back to the client including the specific
     * [NotaryException][net.corda.v5.ledger.notary.plugin.core.NotaryException] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarizationPayload::class.java)

            val txDetails = validateRequest(requestPayload)

            val request = NotarizationRequest(txDetails.inputs, txDetails.id)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", request.transactionId)
            }

            val otherMemberInfo = memberLookup.lookup(session.counterparty)
                ?: throw IllegalStateException("Could not find counterparty on the network: ${session.counterparty}")

            // CORE-11837: Use ledger key
            val otherParty = Party(otherMemberInfo.name, otherMemberInfo.sessionInitiationKeys.first())

            validateRequestSignature(
                request,
                otherParty,
                serializationService,
                signatureVerifier,
                requestPayload.requestSignature,
                digestService
            )

            verifyTransaction(requestPayload)

            if (logger.isTraceEnabled) {
                logger.trace("Requesting uniqueness check for transaction {}", txDetails.id)
            }

            val uniquenessResult = clientService.requestUniquenessCheck(
                txDetails.id.toString(),
                txDetails.inputs.map { it.toString() },
                txDetails.references.map { it.toString() },
                txDetails.numOutputs,
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )

            if (logger.isDebugEnabled) {
                logger.debug("Uniqueness check completed for transaction {}, result is: {}. Sending response to {}",
                    txDetails.id, uniquenessResult, session.counterparty)
            }

            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                transactionSignatureService.signBatch(listOf(txDetails), listOf(requestPayload.notaryKey)).first().first()
            } else null

            session.send(uniquenessResult.toNotarizationResponse(txDetails.id, signature))
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e ${e.stackTraceToString()}")
            session.send(
                NotarizationResponse(
                    emptyList(),
                    NotaryExceptionGeneral("Error while processing request from client. " +
                            "Please contact notary operator for further details.")
                )
            )
        }
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * @throws IllegalStateException if the request could not be validated.
     */
    @Suspendable
    @Suppress("TooGenericExceptionCaught")
    private fun validateRequest(requestPayload: NonValidatingNotarizationPayload): NonValidatingNotaryTransactionDetails {
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
    private fun extractParts(requestPayload: NonValidatingNotarizationPayload): NonValidatingNotaryTransactionDetails {
        val filteredTx = requestPayload.transaction as UtxoFilteredTransaction
        // The notary component is not needed by us but we validate that it is present just in case
        requireNotNull(filteredTx.notary) {
            "Notary component could not be found on the transaction"
        }

        requireNotNull(filteredTx.metadata) {
            "Metadata component could not be found on the transaction"
        }

        requireNotNull(filteredTx.timeWindow) {
            "Time window component could not be found on the transaction"
        }

        val inputStates = filteredTx.inputStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch input states from the filtered transaction"
        }

        val refStates = filteredTx.referenceStateRefs.castOrThrow<UtxoFilteredData.Audit<StateRef>> {
            "Could not fetch reference states from the filtered transaction"
        }

        val outputStates = filteredTx.outputStateAndRefs.castOrThrow<UtxoFilteredData.SizeOnly<StateAndRef<*>>> {
            "Could not fetch output states from the filtered transaction"
        }

        return NonValidatingNotaryTransactionDetails(
            filteredTx.id,
            filteredTx.metadata,
            outputStates.size,
            filteredTx.timeWindow!!,
            inputStates.values.values.toList(),
            refStates.values.values.toList(),
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
    private fun verifyTransaction(requestPayload: NonValidatingNotarizationPayload) {
        try {
            (requestPayload.transaction as UtxoFilteredTransaction).verify()
        } catch (e: Exception) {
            logger.warn(
                "Error while validating transaction ${(requestPayload.transaction as UtxoFilteredTransaction).id}, reason: ${e.message}"
            )
            throw IllegalStateException(
                "Error while validating transaction ${(requestPayload.transaction as UtxoFilteredTransaction).id}",
                e
            )
        }
    }

    private inline fun <reified T> Any.castOrThrow(error: () -> String) = this as? T
        ?: throw java.lang.IllegalStateException(error())

}
