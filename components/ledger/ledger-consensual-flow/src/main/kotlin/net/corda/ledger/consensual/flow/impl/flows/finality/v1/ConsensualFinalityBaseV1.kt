package net.corda.ledger.consensual.flow.impl.flows.finality.v1

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.consensual.data.transaction.verifier.verifyMetadata
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.debug
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.slf4j.Logger

@CordaSystemFlow
abstract class ConsensualFinalityBaseV1 : SubFlow<ConsensualSignedTransaction> {

    abstract val log: Logger

    @CordaInject
    lateinit var persistenceService: ConsensualLedgerPersistenceService

    @Suspendable
    protected fun verifySignature(
        transaction: ConsensualSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata,
        sessionToNotify: FlowSession? = null
    ) {
        try {
            transaction.verifySignature(signature)
            log.debug {
                "Successfully verified signature($signature) by ${signature.by} (key id) for transaction $transaction.id"
            }
        } catch (e: Exception) {
            val message = "Failed to verify transaction signature($signature) by ${signature.by} (key id) for " +
                "transaction ${transaction.id}. Message: ${e.message}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            sessionToNotify?.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            throw e
        }
    }

    @Suspendable
    protected fun verifyAndAddSignature(
        transaction: ConsensualSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ): ConsensualSignedTransactionInternal {
        verifySignature(transaction, signature)
        return transaction.addSignature(signature)
    }

    @Suspendable
    protected fun verifyTransaction(signedTransaction: ConsensualSignedTransactionInternal) {
        try {
            verifyMetadata(signedTransaction.wireTransaction.metadata)
            ConsensualLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verify()
        } catch (e: Exception) {
            persistInvalidTransaction(signedTransaction)
            throw e
        }
    }

    @Suspendable
    protected fun persistInvalidTransaction(transaction: ConsensualSignedTransaction) {
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.debug { "Recorded transaction as invalid: ${transaction.id}" }
    }

    @Suspendable
    protected fun verifyExistingSignatures(
        initialTransaction: ConsensualSignedTransactionInternal,
        sessionToNotify: FlowSession? = null
    ) {
        if (initialTransaction.signatures.isEmpty()) {
            val message = "Received initial transaction without signatures."
            log.warn(message)
            persistInvalidTransaction(initialTransaction)
            sessionToNotify?.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            throw CordaRuntimeException(message)
        }
        initialTransaction.signatures.forEach {
            verifySignature(initialTransaction, it, sessionToNotify)
        }
    }
}
