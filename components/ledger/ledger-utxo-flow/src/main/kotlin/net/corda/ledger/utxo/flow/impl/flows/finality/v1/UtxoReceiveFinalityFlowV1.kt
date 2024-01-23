package net.corda.ledger.utxo.flow.impl.flows.finality.v1

import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityPayload
import net.corda.ledger.utxo.flow.impl.flows.finality.addTransactionIdToFlowContext
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.flows.finality.v1.FinalityNotarizationFailureType.Companion.toFinalityNotarizationFailureType
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData.Audit
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * V1 changed slightly between 5.0 and 5.1.
 * (5.1's initial payload contains the number of parties to let bypass steps later not needed for two parties cases)
 * This change is not managed through flow versioning since flow interoperability is not supported between these versions.
 */

@CordaSystemFlow
class UtxoReceiveFinalityFlowV1(
    private val session: FlowSession,
    private val validator: UtxoTransactionValidator
) : UtxoFinalityBaseV1() {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(UtxoReceiveFinalityFlowV1::class.java)
    }

    override val log: Logger = UtxoReceiveFinalityFlowV1.log

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookupInternal

    @CordaInject
    lateinit var utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService

    @CordaInject
    lateinit var signedGroupParametersVerifier: SignedGroupParametersVerifier

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val (
            initialTransaction,
            transferAdditionalSignatures,
            inputStateAndRefs,
            referenceStateAndRefs
        ) = receiveTransactionAndBackchain()
        val transactionId = initialTransaction.id
        addTransactionIdToFlowContext(flowEngine, transactionId)
        verifyExistingSignatures(initialTransaction, session)
        verifyTransaction(initialTransaction, inputStateAndRefs, referenceStateAndRefs)
        var transaction = if (validateTransaction(initialTransaction, inputStateAndRefs, referenceStateAndRefs)) {
            if (log.isTraceEnabled) {
                log.trace("Successfully validated transaction: $transactionId")
            }
            val (transaction, payload) = signTransaction(initialTransaction)
            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            if (log.isDebugEnabled) {
                log.debug("Recorded transaction with the initial and our signatures: $transactionId")
            }
            session.send(payload)
            transaction
        } else {
            log.warn("Failed to validate transaction: ${initialTransaction.id}")
            persistInvalidTransaction(initialTransaction)
            val payload = Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Transaction validation failed for transaction $transactionId when signature was requested"
            )
            session.send(payload)
            throw CordaRuntimeException(payload.message)
        }

        transaction = receiveAndPersistSignaturesOrSkip(transaction, transferAdditionalSignatures)
        transaction = receiveNotarySignaturesAndAddToTransaction(transaction)
        persistNotarizedTransaction(transaction)
        return transaction
    }

    @Suspendable
    private fun receiveAndPersistSignaturesOrSkip(
        transaction: UtxoSignedTransactionInternal,
        transferAdditionalSignatures: Boolean
    ): UtxoSignedTransactionInternal {
        return if (transferAdditionalSignatures) {
            receiveSignaturesAndAddToTransaction(transaction).let { (it, startingIndex, signatures) ->
                verifyAllReceivedSignatures(it)
                persistenceService.persistTransactionSignatures(it.id, startingIndex, signatures)
                it
            }
        } else {
            verifyAllReceivedSignatures(transaction)
            transaction
        }
    }

    @Suspendable
    private fun receiveTransactionAndBackchain(): InitialTransactionPayload {
        val payload = session.receive(FinalityPayload::class.java)
        val initialTransaction = payload.initialTransaction
        val transferAdditionalSignatures = payload.transferAdditionalSignatures

        if (log.isDebugEnabled) {
            log.debug("Beginning receive finality for transaction: ${initialTransaction.id}")
        }
        val currentGroupParameters = verifyLatestGroupParametersAreUsed(initialTransaction)
        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(currentGroupParameters)
        val notaryInfo = requireNotNull(notaryLookup.lookup(initialTransaction.notaryName)) {
            "Notary ${initialTransaction.notaryName} does not exist in the network"
        }
        log.info("receiveTransactionAndBackchain on ${initialTransaction.id}: backchain required ${notaryInfo.isBackchainRequired}")
        return if (notaryInfo.isBackchainRequired) {
            val transactionDependencies = initialTransaction.dependencies
            if (transactionDependencies.isNotEmpty()) {
                try {
                    flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
                } catch (e: InvalidBackchainException) {
                    log.warn(
                        "Invalid transaction found during back-chain resolution, marking transaction with ID " +
                            "${initialTransaction.id} as invalid.",
                        e
                    )
                    persistInvalidTransaction(initialTransaction)
                    throw e
                }
            } else {
                log.trace {
                    "Transaction with id ${initialTransaction.id} has no dependencies so backchain resolution will not be performed."
                }
            }
            InitialTransactionPayload(initialTransaction, transferAdditionalSignatures, emptyList(), emptyList())
        } else {
            val filteredTransactionsAndSignatures = payload.filteredTransactionsAndSignatures
            requireNotNull(filteredTransactionsAndSignatures) {
                "filtered transaction and signatures cannot be found."
            }
            verifyDependenciesAndPersist(filteredTransactionsAndSignatures, initialTransaction, transferAdditionalSignatures)
        }
    }

    @Suspendable
    private fun verifyDependenciesAndPersist(
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>,
        initialTransaction: UtxoSignedTransactionInternal,
        transferAdditionalSignatures: Boolean
    ): InitialTransactionPayload {
        if (filteredTransactionsAndSignatures.isEmpty()) {
            return InitialTransactionPayload(initialTransaction, transferAdditionalSignatures, emptyList(), emptyList())
        }
        val groupParameters = groupParametersLookup.currentGroupParameters
        val notary = requireNotNull(groupParameters.notaries.first { it.name == initialTransaction.notaryName }) {
            "Notary from initial transaction \"${initialTransaction.notaryName}\" cannot be found in group parameter notaries."
        }

        val dependentStateAndRefs = filteredTransactionsAndSignatures.flatMap { filteredTransactionAndSignatures ->

            filteredTransactionAndSignatures.verifyFilteredTransactionAndSignatures(
                notary,
                notarySignatureVerificationService
            )

            (filteredTransactionAndSignatures.filteredTransaction.outputStateAndRefs as Audit<StateAndRef<*>>).values.values
        }.associateBy { stateRef ->
            stateRef.ref
        }

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

        persistenceService.persistFilteredTransactionsAndSignatures(filteredTransactionsAndSignatures)

        return InitialTransactionPayload(
            initialTransaction,
            transferAdditionalSignatures,
            inputStateAndRefs,
            referenceStateAndRefs
        )
    }

    @Suspendable
    private fun verifyLatestGroupParametersAreUsed(initialTransaction: UtxoSignedTransactionInternal): SignedGroupParameters {
        val currentGroupParameters = groupParametersLookup.currentGroupParameters
        val txGroupParametersHash =
            (initialTransaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()
        if (txGroupParametersHash != currentGroupParameters.hash.toString()) {
            val message =
                "Transactions can be created only with the latest membership group parameters. " +
                    "Current: ${currentGroupParameters.hash} Transaction's: $txGroupParametersHash"
            log.warn(message)
            persistInvalidTransaction(initialTransaction)
            throw CordaRuntimeException(message)
        }
        signedGroupParametersVerifier.verifySignature(currentGroupParameters)
        return currentGroupParameters
    }

    @Suspendable
    private fun verifyTransaction(
        initialTransaction: UtxoSignedTransactionInternal,
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ) {
        val ledgerTransaction = if (inputStateAndRefs.isNotEmpty() || referenceStateAndRefs.isNotEmpty()) {
            utxoLedgerTransactionFactory.createWithStateAndRefs(
                initialTransaction.wireTransaction,
                inputStateAndRefs,
                referenceStateAndRefs
            )
        } else {
            initialTransaction.toLedgerTransaction()
        }
        try {
            transactionVerificationService.verify(ledgerTransaction)
        } catch (e: Exception) {
            persistInvalidTransaction(initialTransaction)
            throw e
        }
    }

    @Suspendable
    private fun validateTransaction(
        initialTransaction: UtxoSignedTransactionInternal,
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ): Boolean {
        val ledgerTransaction = if (inputStateAndRefs.isNotEmpty() || referenceStateAndRefs.isNotEmpty()) {
            utxoLedgerTransactionFactory.createWithStateAndRefs(
                initialTransaction.wireTransaction,
                inputStateAndRefs,
                referenceStateAndRefs
            )
        } else {
            initialTransaction.toLedgerTransaction()
        }
        return try {
            validator.checkTransaction(ledgerTransaction)
            true
        } catch (e: Exception) {
            // Should we only catch a specific exception type? Otherwise, some errors can be swallowed by this warning.
            // Means contracts can't use [check] or [require] unless we provide our own functions for this.
            if (e is IllegalStateException || e is IllegalArgumentException || e is CordaRuntimeException) {
                if (log.isDebugEnabled) {
                    log.debug("Transaction ${ledgerTransaction.id} failed verification. Message: ${e.message}")
                }
                false
            } else {
                throw e
            }
        }
    }

    @Suspendable
    private fun signTransaction(
        initialTransaction: UtxoSignedTransactionInternal,
    ): Pair<UtxoSignedTransactionInternal, Payload<List<DigitalSignatureAndMetadata>>> {
        if (log.isDebugEnabled) {
            log.debug("Signing transaction: ${initialTransaction.id} with our available required keys.")
        }
        val (transaction, mySignatures) = initialTransaction.addMissingSignatures()
        if (log.isDebugEnabled) {
            log.debug("Signing transaction: ${initialTransaction.id} resulted (${mySignatures.size}) signatures.")
        }
        return transaction to Payload.Success(mySignatures)
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(transaction: UtxoSignedTransactionInternal): TransactionAndReceivedSignatures {
        val initialSignaturesSize = transaction.signatures.size
        if (log.isDebugEnabled) {
            log.debug("Waiting for other parties' signatures for transaction: ${transaction.id}")
        }
        @Suppress("unchecked_cast")
        val otherPartiesSignatures = session.receive(List::class.java) as List<DigitalSignatureAndMetadata>
        var signedTransaction = transaction
        otherPartiesSignatures
            .filter { it !in transaction.signatures }
            .forEach {
                signedTransaction = signedTransaction.addSignature(it)
            }

        return TransactionAndReceivedSignatures(
            signedTransaction,
            initialSignaturesSize,
            transaction.signatures.drop(initialSignaturesSize)
        )
    }

    @Suspendable
    private fun verifyAllReceivedSignatures(transaction: UtxoSignedTransactionInternal) {
        if (log.isDebugEnabled) {
            log.debug("Verifying signatures of transaction: ${transaction.id}")
        }
        try {
            transaction.verifySignatorySignatures()
        } catch (e: Exception) {
            persistInvalidTransaction(transaction)
            throw e
        }
    }

    @Suspendable
    private fun receiveNotarySignaturesAndAddToTransaction(transaction: UtxoSignedTransactionInternal): UtxoSignedTransactionInternal {
        if (log.isDebugEnabled) {
            log.debug("Waiting for Notary's signature for transaction: ${transaction.id}")
        }
        @Suppress("unchecked_cast")
        val notarySignaturesPayload = session.receive(Payload::class.java) as Payload<List<DigitalSignatureAndMetadata>>

        val notarySignatures = when (notarySignaturesPayload) {
            is Payload.Success -> notarySignaturesPayload.getOrThrow()
            is Payload.Failure -> {
                val message = "Notarization failed. Failure received from ${session.counterparty} for transaction " +
                    "${transaction.id} with message: ${notarySignaturesPayload.message}"
                log.warn(message)
                val reason = notarySignaturesPayload.reason
                if (reason != null && reason.toFinalityNotarizationFailureType() == FinalityNotarizationFailureType.FATAL) {
                    persistInvalidTransaction(transaction)
                }
                throw CordaRuntimeException(message)
            }
        }

        if (notarySignatures.isEmpty()) {
            val message = "No notary signature received for transaction: ${transaction.id}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            throw CordaRuntimeException(message)
        }
        if (log.isDebugEnabled) {
            log.debug("Verifying and adding notary signatures for transaction: ${transaction.id}")
        }

        var notarizedTransaction = transaction
        notarySignatures.forEach {
            notarizedTransaction = verifyAndAddNotarySignature(notarizedTransaction, it)
        }

        return notarizedTransaction
    }

    @Suspendable
    private fun persistNotarizedTransaction(notarizedTransaction: UtxoSignedTransactionInternal) {
        val visibleStatesIndexes = notarizedTransaction.getVisibleStateIndexes(visibilityChecker)
        persistenceService.persist(notarizedTransaction, TransactionStatus.VERIFIED, visibleStatesIndexes)
        if (log.isDebugEnabled) {
            log.debug("Recorded transaction with all parties' and the notary's signature ${notarizedTransaction.id}")
        }
    }

    private data class InitialTransactionPayload(
        val initialTransaction: UtxoSignedTransactionInternal,
        val transferAdditionalSignatures: Boolean,
        val inputStateAndRefs: List<StateAndRef<*>>,
        val referenceStateAndRefs: List<StateAndRef<*>>
    )

    private data class TransactionAndReceivedSignatures(
        val transaction: UtxoSignedTransactionInternal,
        val indexOfNewSignatures: Int,
        val orderedNewSignatures: List<DigitalSignatureAndMetadata>
    )
}
