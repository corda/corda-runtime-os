package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator

@CordaSystemFlow
class UtxoReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: UtxoTransactionValidator
) : SubFlow<UtxoSignedTransaction>, UtxoFinalityBase() {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val initialTransaction = receiveTransactionAndBackchain()
        val transactionId = initialTransaction.id
        verifyExistingSignatures(initialTransaction, transactionId)
        verifyTransaction(initialTransaction)
        var transaction = if (validateTransaction(initialTransaction)) {
            log.trace { "Successfully validated transaction: $transactionId" }
            val (transaction, payload) = signTransaction(initialTransaction, transactionId)
            session.send(payload)
            transaction
        } else {
            persistInvalidTransaction(initialTransaction, transactionId)
            val payload = Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Transaction validation failed for transaction $transactionId when signature was requested"
            )
            session.send(payload)
            throw CordaRuntimeException(payload.message)
        }
        transaction = receiveSignaturesAndAddToTransaction(transaction, transactionId)
        verifyAllReceivedSignatures(transaction, transactionId)
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        transaction = receiveNotarySignaturesAndAddToTransaction(transaction, transactionId)
        persistNotarizedTransaction(transaction, transactionId)
        return transaction
    }

    @Suspendable
    private fun receiveTransactionAndBackchain(): UtxoSignedTransactionInternal {
        val initialTransaction = session.receive<UtxoSignedTransactionInternal>()
        log.debug { "Beginning receive finality for transaction: ${initialTransaction.id}" }
        flowEngine.subFlow(TransactionBackchainResolutionFlow(initialTransaction, session))
        return initialTransaction
    }

    @Suspendable
    private fun verifyExistingSignatures(initialTransaction: UtxoSignedTransactionInternal, transactionId: SecureHash) {
        initialTransaction.signatures.forEach {
            verifySignature(transactionId, it) { message ->
                session.send(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(message)
                )
            }
        }
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
                log.debug { "Transaction ${signedTransaction.id} failed verification. Message: ${e.message}" }
                false
            } else {
                throw e
            }
        }
    }

    @Suspendable
    private fun signTransaction(
        initialTransaction: UtxoSignedTransactionInternal,
        transactionId: SecureHash
    ): Pair<UtxoSignedTransactionInternal, Payload<List<DigitalSignatureAndMetadata>>> {
        var transaction = initialTransaction
        val myKeys = memberLookup.getMyLedgerKeys()
        // Which of our keys are required.
        val myExpectedSigningKeys = initialTransaction
            .getMissingSignatories()
            .intersect(myKeys)

        if (myExpectedSigningKeys.isEmpty()) {
            log.debug { "We are not required signer of $transactionId." }
        }

        val mySignatures = myExpectedSigningKeys.map { publicKey ->
            log.debug { "Signing transaction: $transactionId with $publicKey" }
            transaction.sign(publicKey).also {
                transaction = it.first
            }.second
        }

        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with the initial and our signatures: $transactionId" }

        return transaction to Payload.Success(mySignatures)
    }

    @Suspendable
    private fun persistInvalidTransaction(
        initialTransaction: UtxoSignedTransactionInternal,
        transactionId: SecureHash
    ) {
        log.warn("Failed to validate transaction: $transactionId")
        persistenceService.persist(initialTransaction, TransactionStatus.INVALID)
        log.debug { "Recorded transaction as invalid: $transactionId" }
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(
        transaction: UtxoSignedTransactionInternal,
        transactionId: SecureHash
    ): UtxoSignedTransactionInternal {
        log.debug { "Waiting for other parties' signatures for transaction: $transactionId" }
        val otherPartiesSignatures = session.receive<List<DigitalSignatureAndMetadata>>()

        var signedTransaction = transaction
        otherPartiesSignatures
            .filter { it !in transaction.signatures }
            .forEach {
                signedTransaction = transaction.addSignature(it)
            }

        return signedTransaction
    }

    @Suspendable
    private fun verifyAllReceivedSignatures(transaction: UtxoSignedTransactionInternal, transactionId: SecureHash) {
        log.debug { "Verifying signatures of transaction: $transactionId" }
        transaction.verifySignatures()
    }

    @Suspendable
    private fun receiveNotarySignaturesAndAddToTransaction(
        transaction: UtxoSignedTransactionInternal,
        transactionId: SecureHash
    ): UtxoSignedTransactionInternal {
        log.debug { "Waiting for Notary's signature for transaction: $transactionId" }
        val notarySignaturesPayload = session.receive<Payload<List<DigitalSignatureAndMetadata>>>()

        val notarySignatures = notarySignaturesPayload.getOrThrow { failure ->
            val message = "Notarisation failed. Failure received from ${session.counterparty} for transaction " +
                    "$transactionId with message: ${failure.message}"
            log.warn(message)
            CordaRuntimeException(message)
        }

        if (notarySignatures.isEmpty()) {
            val message = "No notary signature received for transaction: $transactionId"
            log.warn(message)
            throw CordaRuntimeException(message)

        }
        log.debug { "Verifying and adding notary signatures for transaction: $transactionId" }

        var notarizedTransaction = transaction
        notarySignatures.forEach {
            notarizedTransaction = verifyAndAddNotarySignature(transaction, it)
        }

        return notarizedTransaction
    }

    @Suspendable
    private fun persistNotarizedTransaction(notarizedTransaction: UtxoSignedTransactionInternal, transactionId: SecureHash) {
        val relevantStatesIndexes = notarizedTransaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(notarizedTransaction, TransactionStatus.VERIFIED, relevantStatesIndexes)
        log.debug { "Recorded transaction with all parties' and the notary's signature $transactionId" }
    }
}
