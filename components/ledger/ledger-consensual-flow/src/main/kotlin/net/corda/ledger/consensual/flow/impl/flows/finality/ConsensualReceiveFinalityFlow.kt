package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ConsensualReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: ConsensualTransactionValidator
) : ConsensualFinalityBase() {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var persistenceService: ConsensualLedgerPersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {
        val initialTransaction = session.receive<ConsensualSignedTransactionInternal>()
        val transactionId = initialTransaction.id

        verifyExistingSignatures(initialTransaction)
        verifyTransaction(initialTransaction)

        var transaction = if (validateTransaction(initialTransaction)) {
            log.trace { "Successfully validated transaction: $transactionId" }
            val (transaction, payload) = signTransaction(initialTransaction)
            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            log.debug { "Recorded transaction with the initial and our signatures: $transactionId" }
            session.send(payload)
            transaction
        } else {
            persistInvalidTransaction(initialTransaction)
            val payload = Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Transaction validation failed for transaction $transactionId when signature was requested"
            )
            session.send(payload)
            throw CordaRuntimeException(payload.message)
        }

        transaction = receiveSignaturesAndAddToTransaction(transaction)

        log.debug { "Verifying signatures of transaction: $transactionId" }
        transaction.verifySignatures()

        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }

        return transaction
    }

    @Suspendable
    private fun verifyExistingSignatures(initialTransaction: ConsensualSignedTransactionInternal) {
        initialTransaction.signatures.forEach {
            verifySignature(initialTransaction.id, it) { message ->
                session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            }
        }
    }

    @Suspendable
    private fun validateTransaction(signedTransaction: ConsensualSignedTransaction): Boolean {
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

    private fun verifyTransaction(signedTransaction: ConsensualSignedTransaction) {
        ConsensualLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verify()
    }

    @Suspendable
    private fun signTransaction(
        initialTransaction: ConsensualSignedTransactionInternal,
    ): Pair<ConsensualSignedTransactionInternal, Payload<List<DigitalSignatureAndMetadata>>> {
        log.debug { "Signing transaction: ${initialTransaction.id} with our available required keys." }
        val (transaction, mySignatures) = initialTransaction.addMissingSignatures()
        log.debug { "Signing transaction: ${initialTransaction.id} resulted (${mySignatures.size}) signatures." }
        return transaction to Payload.Success(mySignatures)
    }

    @Suspendable
    private fun persistInvalidTransaction(transaction: ConsensualSignedTransactionInternal) {
        log.warn("Failed to validate transaction: ${transaction.id}")
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.debug { "Recorded transaction as invalid: ${transaction.id}" }
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(
        transaction: ConsensualSignedTransactionInternal
    ): ConsensualSignedTransactionInternal {
        log.debug { "Waiting for other parties' signatures for transaction: ${transaction.id}" }
        val otherPartiesSignatures = session.receive<List<DigitalSignatureAndMetadata>>()
        var signedTransaction = transaction
        otherPartiesSignatures
            .filter { it !in transaction.signatures }
            .forEach {
                signedTransaction = signedTransaction.addSignature(it)
            }

        return signedTransaction
    }
}