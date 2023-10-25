package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.NotaryFilteredTransactionDetails
import com.r3.corda.notary.plugin.common.toNotarizationResponse
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import com.r3.corda.notary.plugin.contractverifying.api.FilteredTransactionAndSignatures
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey

@InitiatedBy(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryServerFlowImpl : ResponderFlow {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @Suspendable
    override fun call(session: FlowSession) {
        try {

            // 1. Receive the payload from the client
            val requestPayload = session.receive(ContractVerifyingNotarizationPayload::class.java)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", requestPayload.initialFilteredTransaction.id)
            }

            logger.info("Received notarization request for transaction {}", requestPayload.initialFilteredTransaction.id)

            // 2. Extract the data from the filtered transaction
            val txDetails = extractParts(requestPayload)

            if (logger.isTraceEnabled) {
                logger.trace("Verifying signatures for the following dependencies: {}",
                    requestPayload.filteredTransactionsAndSignatures.map { it.filteredTransaction.id })
            }

            logger.info("Verifying signatures for the following dependencies: {}",
                requestPayload.filteredTransactionsAndSignatures.map { it.filteredTransaction.id })

            // 3. Verify the dependencies' signatures
            verifySignatures(requestPayload.notaryKey, requestPayload.filteredTransactionsAndSignatures)

            if (logger.isTraceEnabled) {
                logger.trace("Requesting uniqueness check for transaction {}", txDetails.id)
            }

            logger.info("Requesting uniqueness check for transaction {}", txDetails.id)

            // 4. Request a uniqueness check on the transaction
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

            logger.info("Uniqueness check completed for transaction {}, result is: {}. Sending response to {}",
                txDetails.id, uniquenessResult, session.counterparty)

            // 5. Sign the request if the uniqueness check was successful
            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                transactionSignatureService.signBatch(
                    listOf(txDetails), // TODO Batch size is always just one for now
                    listOf(requestPayload.notaryKey)
                ).first().first()
            } else null

            // 6. Send the response back to the client
            session.send(uniquenessResult.toNotarizationResponse(txDetails.id, signature))
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e ${e.stackTraceToString()}")
            session.send(
                NotarizationResponse(
                    emptyList(),
                    NotaryExceptionGeneral(
                        "Error while processing request from client. " +
                                "Please contact notary operator for further details."
                    )
                )
            )
        }
    }

    /**
     * A function that will verify the signatures of the given [filteredTransactionsAndSignatures].
     */
    private fun verifySignatures(
        notaryKey: PublicKey,
        filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>
    ) {
        filteredTransactionsAndSignatures.forEach { (filteredTransaction, signatures) ->
            require(signatures.isNotEmpty()) { "No notary signatures were received" }
            filteredTransaction.verify()
            for (signature in signatures) {
                transactionSignatureService.verifySignature(
                    filteredTransaction,
                    signature,
                    notaryKey
                )
                logger.info("SUCCESSFULLY VERIFIED $filteredTransaction | $signatures")
            }
        }
    }

    /**
     * A helper function that constructs an instance of [NotaryFilteredTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun extractParts(requestPayload: ContractVerifyingNotarizationPayload): NotaryFilteredTransactionDetails {
        val filteredTx = requestPayload.initialFilteredTransaction

        // The notary component is not needed by us, but we validate that it is present just in case
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

        return NotaryFilteredTransactionDetails(
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

    private inline fun <reified T> Any.castOrThrow(error: () -> String) = this as? T
        ?: throw java.lang.IllegalStateException(error())
}