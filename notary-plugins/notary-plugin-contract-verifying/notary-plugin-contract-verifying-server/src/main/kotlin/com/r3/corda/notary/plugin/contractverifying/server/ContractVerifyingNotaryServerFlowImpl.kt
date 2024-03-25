package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionInvalidSignature
import com.r3.corda.notary.plugin.common.NotaryExceptionTransactionVerificationFailure
import com.r3.corda.notary.plugin.common.NotaryTransactionDetails
import com.r3.corda.notary.plugin.common.toNotarizationResponse
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.core.NotaryException
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
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

    @CordaInject
    private lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    /**
     * Constructor used for testing to initialize the necessary services
     */
    @VisibleForTesting
    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        transactionSignatureService: TransactionSignatureService,
        utxoLedgerService: UtxoLedgerService,
        memberLookup: MemberLookup,
        notarySignatureVerificationService: NotarySignatureVerificationService
    ) : this() {
        this.clientService = clientService
        this.transactionSignatureService = transactionSignatureService
        this.utxoLedgerService = utxoLedgerService
        this.memberLookup = memberLookup
        this.notarySignatureVerificationService = notarySignatureVerificationService
    }

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val (
                initialTransaction,
                filteredTransactionsAndSignatures,
                notarizationType
            ) = session.receive(ContractVerifyingNotarizationPayload::class.java)

            if (logger.isTraceEnabled) {
                logger.trace("Received notarization request ($notarizationType) for transaction {}", initialTransaction.id)
            }

            val initialTransactionDetails = getInitialTransactionDetail(initialTransaction)

            validateTransactionNotaryAgainstCurrentNotary(initialTransactionDetails)

            verifySignatures(initialTransaction.notaryKey, filteredTransactionsAndSignatures, initialTransaction)

            verifyTransaction(initialTransaction, filteredTransactionsAndSignatures)

            if (logger.isTraceEnabled) {
                logger.trace("Requesting uniqueness check ($notarizationType) for transaction {}", initialTransactionDetails.id)
            }

            val uniquenessResult = checkUniqueness(initialTransactionDetails, session, notarizationType)

            if (logger.isDebugEnabled) {
                logger.debug(
                    "Uniqueness check ($notarizationType) completed for transaction {}, result is: {}. Sending response to {}",
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
            logger.warn("Error while processing request from client", e)
            val genericMessage = "Error while processing request from client"
            val notaryException = when (e) {
                is NotaryException -> e
                // [IllegalArgumentException]s are thrown if a transaction does not pass our correctness checks.
                is IllegalArgumentException -> NotaryExceptionTransactionVerificationFailure("$genericMessage. Cause: ${e.message}")
                else -> NotaryExceptionGeneral("$genericMessage. Please contact notary operator for further details.")
            }
            session.send(NotarizationResponse(emptyList(), notaryException))
        }
    }

    /**
     * A helper function that constructs an instance of [NotaryTransactionDetails] from the given transaction.
     */
    @Suspendable
    private fun getInitialTransactionDetail(
        initialTransaction: UtxoSignedTransaction,
    ): NotaryTransactionDetails {
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
    fun verifySignatures(
        notaryKey: PublicKey,
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>,
        initialTransaction: UtxoSignedTransaction
    ) {
        initialTransaction.verifySignatorySignatures()

        val keyIdToNotaryKeys: MutableMap<String, MutableMap<SecureHash, PublicKey>> = mutableMapOf()

        filteredTransactionsAndSignatures.forEach {
            it.filteredTransaction.verify()
            try {
                notarySignatureVerificationService.verifyNotarySignatures(
                    it.filteredTransaction,
                    notaryKey,
                    it.signatures,
                    keyIdToNotaryKeys
                )
            } catch (e: Exception) {
                throw NotaryExceptionInvalidSignature(
                    "A valid notary signature is not found with error message: ${e.message}."
                )
            }
        }
    }

    @Suspendable
    private fun verifyTransaction(
        initialTransaction: UtxoSignedTransaction,
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>
    ) {
        try {
            val dependentStateAndRefs = filteredTransactionsAndSignatures.flatMap {
                (it.filteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values
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
        } catch (e: Exception) {
            throw NotaryExceptionTransactionVerificationFailure(
                "Transaction failed to verify with error message: ${e.message}.",
                initialTransaction.id
            )
        }
    }

    @Suspendable
    private fun checkUniqueness(
        txDetails: NotaryTransactionDetails,
        session: FlowSession,
        notarizationType: NotarizationType
    ): UniquenessCheckResult {
        return when (notarizationType) {
            NotarizationType.WRITE -> clientService.requestUniquenessCheckWrite(
                txDetails.id.toString(),
                session.counterparty.toString(),
                txDetails.inputs.map { it.toString() },
                txDetails.references.map { it.toString() },
                txDetails.numOutputs,
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )
            NotarizationType.READ -> clientService.requestUniquenessCheckRead(
                txDetails.id.toString(),
                session.counterparty.toString(),
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )
            else -> throw IllegalArgumentException("Received invalid notarization type $notarizationType")
        }
    }
}