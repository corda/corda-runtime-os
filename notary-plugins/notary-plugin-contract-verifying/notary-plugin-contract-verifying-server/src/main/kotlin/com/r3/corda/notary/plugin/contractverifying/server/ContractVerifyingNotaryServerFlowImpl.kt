package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.NotaryTransactionDetails
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
import org.slf4j.LoggerFactory
import java.security.PublicKey

@InitiatedBy(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryServerFlowImpl : ResponderFlow {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            logger.info("Calling Contract Verifying Notary server...")

            // Receive the payload from the client
            val (initialTransaction, filteredTransactionsAndSignatures) = session.receive(ContractVerifyingNotarizationPayload::class.java)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", initialTransaction.id)
            }

            // Extract the data from the signed transaction
            val initialTransactionDetails = getInitialTransactionDetail(initialTransaction)

            // Verify the signatures
            if (logger.isTraceEnabled) {
                logger.trace(
                    "Verifying signatures for the following dependencies: {}",
                    filteredTransactionsAndSignatures.map { it.filteredTransaction.id })
            }

            verifySignatures(initialTransaction.notaryKey, filteredTransactionsAndSignatures)

            // Verify the contract
            verifyContract(initialTransaction, filteredTransactionsAndSignatures)

            // Request a uniqueness check on the transaction
            if (logger.isTraceEnabled) {
                logger.trace("Requesting uniqueness check for transaction {}", initialTransactionDetails.id)
            }

            val uniquenessResult = clientService.requestUniquenessCheck(
                initialTransaction.id.toString(),
                session.counterparty.toString(),
                initialTransaction.inputStateRefs.map { it.toString() },
                initialTransaction.referenceStateRefs.map { it.toString() },
                initialTransaction.outputStateAndRefs.count(),
                initialTransaction.timeWindow.from,
                initialTransaction.timeWindow.until
            )

            if (logger.isDebugEnabled) {
                logger.debug(
                    "Uniqueness check completed for transaction {}, result is: {}. Sending response to {}",
                    initialTransaction.id, uniquenessResult, session.counterparty
                )
            }

            // Sign the request if the uniqueness check was successful
            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                transactionSignatureService.signBatch(
                    listOf(initialTransactionDetails),
                    listOf(initialTransaction.notaryKey)
                ).first().first()
            } else null

            // Send the response back to the client
            session.send(uniquenessResult.toNotarizationResponse(initialTransactionDetails.id, signature))
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e ${e.stackTraceToString()}")

            session.send(
                NotarizationResponse(
                    emptyList(),
                    NotaryExceptionGeneral(
                        "Error while processing request from client. " +
                        "Please contract notary operator for further details."
                    )
                )
            )
        }
    }

    /**
     * A helper function that constructs an instance of [NotaryTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun getInitialTransactionDetail(initialTransaction: UtxoSignedTransaction): NotaryTransactionDetails {
        return NotaryTransactionDetails(
            initialTransaction.id,
            initialTransaction.metadata,
            initialTransaction.outputStateAndRefs.count(),
            initialTransaction.timeWindow,
            initialTransaction.inputStateRefs,
            initialTransaction.referenceStateRefs,
            initialTransaction.notaryName,
            initialTransaction.notaryKey
        )
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
                    filteredTransaction.id,
                    signature,
                    notaryKey
                )
            }
            logger.info("SUCCESSFULLY VERIFIED $filteredTransaction | $signatures")
        }
    }

    @Suspendable
    private fun verifyContract(
        initialTransaction: UtxoSignedTransaction,
        filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>
    ) {
        val dependentStateAndRefs = filteredTransactionsAndSignatures.flatMap { (filteredTransaction, _) ->
            (filteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values
        }.associateBy { it.ref }

        println(dependentStateAndRefs)

        val inputStateAndRefs = initialTransaction.inputStateRefs.map { stateRef ->
            requireNotNull(dependentStateAndRefs[stateRef]) {
                "Missing input state and ref from the filtered transaction"
            }
        }

        val referenceStateAndRefs = initialTransaction.referenceStateRefs.map { stateRef ->
            requireNotNull(dependentStateAndRefs[stateRef]) {
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
}