package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.ledger.consensual.flow.impl.transaction.verifier.verifyMetadata
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
abstract class ConsensualFinalityBase : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    lateinit var persistenceService: ConsensualLedgerPersistenceService

    @Suspendable
    protected fun verifySignature(
        transaction: TransactionWithMetadata,
        signature: DigitalSignatureAndMetadata,
        onFailure: ((message: String) -> Unit)? = null
    ) {
        try {
            transactionSignatureService.verifySignature(transaction, signature)
            log.debug { "Successfully verified signature($signature) by ${signature.by.encoded} (encoded) for transaction $transaction.id" }
        } catch (e: Exception) {
            val message = "Failed to verify transaction's signature($signature) by ${signature.by.encoded} (encoded) for " +
                    "transaction ${transaction.id}. Message: ${e.message}"
            log.warn(message)
            if (onFailure != null)
                onFailure(message)
            throw e
        }
    }

    @Suspendable
    protected fun verifyAndAddSignature(
        transaction: ConsensualSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ): ConsensualSignedTransactionInternal {
        verifySignature(transaction, signature) {
            persistInvalidTransaction(transaction)
        }
        return transaction.addSignature(signature)
    }

    protected fun verifyTransaction(signedTransaction: ConsensualSignedTransactionInternal) {
        verifyMetadata(signedTransaction.wireTransaction.metadata)
        ConsensualLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verify()
    }

    @Suspendable
    protected fun persistInvalidTransaction(transaction: ConsensualSignedTransactionInternal) {
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.debug { "Recorded transaction as invalid: ${transaction.id}" }
    }
}