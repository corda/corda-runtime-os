package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualTransactionVerification
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator

@CordaSystemFlow
class ConsensualReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: ConsensualTransactionValidator
) : ConsensualFinalityBase() {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var memberLookup: MemberLookup

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

        // TODO [CORE-5982] Verify already added signatures.
         var transaction = if (validateTransaction(initialTransaction)) {
            log.trace { "Successfully validated transaction: $transactionId" }
            val (transaction, payload) = signTransaction(initialTransaction)
            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            log.debug { "Recorded transaction with the initial and our signatures: $transactionId" }
            session.send(payload)
             transaction
        } else {
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
        log.debug { "Recorded signed transaction $transactionId" }

        acknowledgeFinalizedTransaction(transaction)

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

    private fun verifyTransaction(signedTransaction: ConsensualSignedTransaction){
        val ledgerTransactionToCheck = signedTransaction.toLedgerTransaction()
        ConsensualTransactionVerification.verifyLedgerTransaction(ledgerTransactionToCheck)
    }

    @Suspendable
    private fun signTransaction(
        initialTransaction: ConsensualSignedTransactionInternal,
    ): Pair<ConsensualSignedTransactionInternal, Payload<List<DigitalSignatureAndMetadata>>> {
        val myKeys = memberLookup.myInfo()
            .ledgerKeys
            .toSet()
        // Which of our keys are required.
        val myExpectedSigningKeys = initialTransaction
            .getMissingSignatories()
            .intersect(myKeys)

        if (myExpectedSigningKeys.isEmpty()) {
            log.debug { "We are not required signer of ${initialTransaction.id}." }
        }

        var transaction = initialTransaction
        val mySignatures = myExpectedSigningKeys.map { publicKey ->
            log.debug { "Signing transaction: ${transaction.id} with $publicKey" }
            transaction.sign(publicKey).also {
                transaction = it.first
            }.second
        }

        return transaction to Payload.Success(mySignatures)
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

    @Suspendable
    private fun acknowledgeFinalizedTransaction(transaction: ConsensualSignedTransactionInternal) {
        session.send(Unit)
        log.trace { "Sent acknowledgement to initiator of finality for signed transaction ${transaction.id}" }
    }
}