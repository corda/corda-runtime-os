package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.toNotarizationResponse
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarizationPayload
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
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
 * This will be initiated by the client side of this notary plugin,
 * [NonValidatingNotaryClientFlowImpl][com.r3.corda.notary.plugin.nonvalidating.client.NonValidatingNotaryClientFlowImpl]
 */
@InitiatedBy(protocol = "com.r3.corda.notary.plugin.nonvalidating", version = [1])
class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        transactionSignatureService: TransactionSignatureService,
        memberLookup: MemberLookup
    ) : this() {
        this.clientService = clientService
        this.transactionSignatureService = transactionSignatureService
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
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.NotarizationResponse]
     * back to the client including the specific
     * [NotaryException][net.corda.v5.ledger.notary.plugin.core.NotaryException] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarizationPayload::class.java)

            val txDetails = validateRequest(requestPayload)

            validateTransactionNotaryAgainstCurrentNotary(txDetails)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", txDetails.id)
            }

            verifyTransaction(requestPayload)

            if (logger.isTraceEnabled) {
                logger.trace("Requesting uniqueness check for transaction {}", txDetails.id)
            }

            val uniquenessResult = clientService.requestUniquenessCheck(
                txDetails.id.toString(),
                session.counterparty.toString(),
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
     * This function will validate selected notary is valid notary to notarize.
     * */
    @Suspendable
    private fun validateTransactionNotaryAgainstCurrentNotary(txDetails: NonValidatingNotaryTransactionDetails) {
        val currentNotaryContext = memberLookup
            .myInfo()
            .memberProvidedContext
        val currentNotaryServiceName = currentNotaryContext
            .parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)
        val currentNotaryBackchainRequired = currentNotaryContext
            .parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)

        require(currentNotaryServiceName == txDetails.notaryName) {
            "Notary service on the transaction ${txDetails.notaryName} does not match the notary service represented" +
                    " by this notary virtual node (${currentNotaryServiceName})"
        }

        require(currentNotaryBackchainRequired) {
            "Non-validating notary can't switch bachchain verification off."
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
        requireNotNull(filteredTx.notaryName) {
            "Notary name component could not be found on the transaction"
        }

        requireNotNull(filteredTx.notaryKey) {
            "Notary key component could not be found on the transaction"
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
            filteredTx.notaryName!!,
            filteredTx.notaryKey!!
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
