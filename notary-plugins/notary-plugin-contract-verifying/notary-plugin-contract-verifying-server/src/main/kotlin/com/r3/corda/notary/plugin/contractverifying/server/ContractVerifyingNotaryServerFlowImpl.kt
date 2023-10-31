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
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
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

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {

            // 1. Receive the payload from the client
            val requestPayload = session.receive(ContractVerifyingNotarizationPayload::class.java)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", requestPayload.initialTransaction.id)
            }

            // 2. Extract the data from the signed transaction
            val txDetails = extractParts(requestPayload)

            // 3. Verify the signatures
            if (logger.isTraceEnabled) {
                logger.trace("Verifying signatures for the following dependencies: {}",
                    requestPayload.filteredTransactionsAndSignatures.map { it.filteredTransaction.id })
            }

            verifySignatures(requestPayload.notaryKey, requestPayload.filteredTransactionsAndSignatures)

            // 4. Verify the contract
            verifyContract(requestPayload.initialTransaction, requestPayload.filteredTransactionsAndSignatures)

            // 5. Request a uniqueness check on the transaction
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
                    requestPayload.initialTransaction.id, uniquenessResult, session.counterparty)
            }

            // 6. Sign the request if the uniqueness check was successful
            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                transactionSignatureService.signBatch(
                    listOf(txDetails), // TODO Batch size is always just one for now
                    listOf(requestPayload.notaryKey)
                ).first().first()
            } else null

            // 7. Send the response back to the client
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
    @Suspendable
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

    @Suspendable
    private fun verifyContract(
        initialTransaction: UtxoSignedTransaction,
        filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>
    ) {
        val dependantStateAndRefs = filteredTransactionsAndSignatures.flatMap { (filteredTransaction, _) ->
            (filteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values
        }.associateBy { it.ref }

        val inputStateAndRefs = initialTransaction.inputStateRefs.map { stateRef ->
            requireNotNull(dependantStateAndRefs[stateRef]) {
                "Missing input state and ref from the filtered transaction"
            }
        }
        val referenceStateAndRefs = initialTransaction.referenceStateRefs.map { stateRef ->
            requireNotNull(dependantStateAndRefs[stateRef]) {
                "Missing reference state and ref from the filtered transaction"
            }
        }

        val ledgerTransaction = if (inputStateAndRefs.isNotEmpty() || referenceStateAndRefs.isNotEmpty()) {
            initialTransaction.toLedgerTransaction(inputStateAndRefs, referenceStateAndRefs)
        } else {
            initialTransaction.toLedgerTransaction()
        }

        utxoLedgerService.verifyContract(ledgerTransaction)
    }

    /**
     * A helper function that constructs an instance of [NotaryFilteredTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun extractParts(requestPayload: ContractVerifyingNotarizationPayload): NotaryFilteredTransactionDetails {

        return NotaryFilteredTransactionDetails(
            requestPayload.initialTransaction.id,
            requestPayload.initialTransaction.metadata,
            requestPayload.initialTransaction.outputStateAndRefs.count(),
            requestPayload.initialTransaction.timeWindow,
            requestPayload.initialTransaction.inputStateRefs,
            requestPayload.initialTransaction.referenceStateRefs,
            requestPayload.initialTransaction.notaryName,
            requestPayload.initialTransaction.notaryKey
        )
    }
}
