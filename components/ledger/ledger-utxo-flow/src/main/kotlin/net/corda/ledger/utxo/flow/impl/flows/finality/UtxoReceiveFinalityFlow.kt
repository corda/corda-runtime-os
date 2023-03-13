package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityNotarizationFailureType.Companion.toFinalityNotarizationFailureType
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class UtxoReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: UtxoTransactionValidator
) : UtxoFinalityBase() {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(UtxoReceiveFinalityFlow::class.java)
    }

    override val log: Logger = UtxoReceiveFinalityFlow.log

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val initialTransaction = receiveTransactionAndBackchain()
        val transactionId = initialTransaction.id
        verifyExistingSignatures(initialTransaction, session)
        verifyTransaction(initialTransaction)
        var transaction = if (validateTransaction(initialTransaction)) {
            if (log.isTraceEnabled) {
                log.trace( "Successfully validated transaction: $transactionId")
            }
            val (transaction, payload) = signTransaction(initialTransaction)
            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            if (log.isDebugEnabled) {
                log.debug( "Recorded transaction with the initial and our signatures: $transactionId")
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
        transaction = receiveSignaturesAndAddToTransaction(transaction)
        verifyAllReceivedSignatures(transaction)
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        transaction = receiveNotarySignaturesAndAddToTransaction(transaction)
        persistNotarizedTransaction(transaction)
        return transaction
    }

    @Suspendable
    private fun receiveTransactionAndBackchain(): UtxoSignedTransactionInternal {
        val initialTransaction = session.receive(UtxoSignedTransactionInternal::class.java)
        if (log.isDebugEnabled) {
            log.debug( "Beginning receive finality for transaction: ${initialTransaction.id}")
        }
        flowEngine.subFlow(TransactionBackchainResolutionFlow(initialTransaction, session))
        return initialTransaction
    }

    @Suspendable
    private fun validateTransaction(signedTransaction: UtxoSignedTransaction): Boolean {
        return try {
            validator.checkTransaction(signedTransaction.toLedgerTransaction())
            true
        } catch (e: Exception) {
            // Should we only catch a specific exception type? Otherwise, some errors can be swallowed by this warning.
            // Means contracts can't use [check] or [require] unless we provide our own functions for this.
            if (e is IllegalStateException || e is IllegalArgumentException || e is CordaRuntimeException) {
                if (log.isDebugEnabled) {
                    log.debug( "Transaction ${signedTransaction.id} failed verification. Message: ${e.message}")
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
            log.debug( "Signing transaction: ${initialTransaction.id} with our available required keys.")
        }
        val (transaction, mySignatures) = initialTransaction.addMissingSignatures()
        if (log.isDebugEnabled) {
            log.debug( "Signing transaction: ${initialTransaction.id} resulted (${mySignatures.size}) signatures.")
        }
        return transaction to Payload.Success(mySignatures)
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(transaction: UtxoSignedTransactionInternal): UtxoSignedTransactionInternal {
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

        return signedTransaction
    }

    @Suspendable
    private fun verifyAllReceivedSignatures(transaction: UtxoSignedTransactionInternal) {
        if (log.isDebugEnabled) {
            log.debug("Verifying signatures of transaction: ${transaction.id}")
        }
        try {
            transaction.verifySignatures()
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

        val notarySignatures = when (notarySignaturesPayload){
            is Payload.Success -> notarySignaturesPayload.getOrThrow()
            is Payload.Failure ->
            {
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
        val relevantStatesIndexes = notarizedTransaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(notarizedTransaction, TransactionStatus.VERIFIED, relevantStatesIndexes)
        if (log.isDebugEnabled) {
            log.debug("Recorded transaction with all parties' and the notary's signature ${notarizedTransaction.id}")
        }
    }
}
