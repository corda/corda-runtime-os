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
) : SubFlow<ConsensualSignedTransaction> {

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

        verifyTransaction(initialTransaction)

        // TODO [CORE-5982] Verify already added signatures.
         if (validateTransaction(initialTransaction)) {
            log.trace { "Successfully validated transaction: $transactionId" }
            val (transaction, payload) = signTransaction(initialTransaction, transactionId)
            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            log.debug { "Recorded transaction with the initial and our signatures: $transactionId" }
            session.send(payload)
        } else {
            val payload = Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Transaction validation failed for transaction $transactionId when signature was requested"
            )
            session.send(payload)
            throw CordaRuntimeException(payload.message)
        }

        val transaction = receiveFinalizedTransaction(transactionId)
        transaction.verifySignatures()

        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded signed transaction $transactionId" }

        acknowledgeFinalizedTransaction(transactionId)

        return transaction
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
        transactionId: SecureHash
    ): Pair<ConsensualSignedTransactionInternal, Payload<List<DigitalSignatureAndMetadata>>> {
        val myKeys = memberLookup.myInfo()
            .ledgerKeys
            .toSet()
        // Which of our keys are required.
        val myExpectedSigningKeys = initialTransaction
            .getMissingSignatories()
            .intersect(myKeys)

        if (myExpectedSigningKeys.isEmpty()) {
            log.debug { "We are not required signer of $transactionId." }
        }

        var transaction = initialTransaction
        val mySignatures = myExpectedSigningKeys.map { publicKey ->
            log.debug { "Signing transaction: $transactionId with $publicKey" }
            transaction.sign(publicKey).also {
                transaction = it.first
            }.second
        }

        return transaction to Payload.Success(mySignatures)
    }

    @Suspendable
    private fun receiveFinalizedTransaction(transactionId: SecureHash): ConsensualSignedTransactionInternal {
        val transaction = session.receive<ConsensualSignedTransactionInternal>()

        // A [require] block isn't the correct option if we want to do something with the error on the peer side
        require(transaction.id == transactionId) {
            "Expected to received transaction $transactionId from ${session.counterparty} to finalise but received " +
                    "${transaction.id} instead"
        }

        return transaction
    }

    @Suspendable
    private fun acknowledgeFinalizedTransaction(transactionId: SecureHash) {
        session.send(Unit)
        log.trace { "Sent acknowledgement to initiator of finality for signed transaction $transactionId" }
    }
}