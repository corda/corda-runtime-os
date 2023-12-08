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
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.LoggerFactory
import java.security.PublicKey

@InitiatedBy(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryServerFlowImpl() : ResponderFlow {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        transactionSignatureService: TransactionSignatureService,
        utxoLedgerService: UtxoLedgerService,
        memberLookup: MemberLookup
    ) : this() {
        this.clientService = clientService
        this.transactionSignatureService = transactionSignatureService
        this.utxoLedgerService = utxoLedgerService
        this.memberLookup = memberLookup
    }

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val (initialTransaction, filteredTransactionsAndSignatures) = session.receive(ContractVerifyingNotarizationPayload::class.java)
            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request for transaction {}", initialTransaction.id)
            }

            val initialTransactionDetails = getInitialTransactionDetail(initialTransaction)

            validateTransactionNotaryAgainstCurrentNotary(initialTransactionDetails)

            verifySignatures(initialTransaction.notaryKey, filteredTransactionsAndSignatures)

            verifyTransaction(initialTransaction, filteredTransactionsAndSignatures)

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

            val signature = if (uniquenessResult is UniquenessCheckResultSuccess) {
                transactionSignatureService.signBatch(
                    listOf(initialTransactionDetails),
                    listOf(initialTransaction.notaryKey)
                ).first().first()
            } else null

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

    @Suspendable
    private fun validateTransactionNotaryAgainstCurrentNotary(txDetails: NotaryTransactionDetails) {
        val currentNotaryContext = memberLookup
            .myInfo()
            .memberProvidedContext
        val currentNotaryServiceName = currentNotaryContext
            .parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)

        require(currentNotaryServiceName == txDetails.notaryName) {
            "Notary service on the transaction ${txDetails.notaryName} does not match the notary service represented" +
                    " by this notary virtual node (${currentNotaryServiceName})"
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
            require(signatures.isNotEmpty()) { "No notary signatures were received with transaction: ${filteredTransaction.id}." }
            filteredTransaction.verify()
            val hasValidSignature = signatures.any {
                try {
                    transactionSignatureService.verifySignature(
                        filteredTransaction.id,
                        it,
                        notaryKey
                    )
                    true
                } catch (e: Exception) {
                    false
                }
            }
            require(hasValidSignature) {
                "A valid notary signature is not found with transaction: ${filteredTransaction.id}."
            }
        }
    }

    @Suspendable
    private fun verifyTransaction(
        initialTransaction: UtxoSignedTransaction,
        filteredTransactionsAndSignatures: List<FilteredTransactionAndSignatures>
    ) {
        val dependentStateAndRefs = filteredTransactionsAndSignatures.flatMap { (filteredTransaction, _) ->
            (filteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values
        }.associateBy { it.ref }

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

        utxoLedgerService.verify(initialTransaction.toLedgerTransaction(inputStateAndRefs, referenceStateAndRefs))
    }
}