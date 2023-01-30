package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualTransactionMetadataVerifier
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
abstract class ConsensualFinalityBase : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @Suspendable
    protected fun verifySignature(
        transactionId: SecureHash,
        signature: DigitalSignatureAndMetadata,
        onFailure: ((message: String) -> Unit)? = null
    ) {
        try {
            transactionSignatureService.verifySignature(transactionId, signature)
            log.debug { "Successfully verified signature($signature) by ${signature.by.encoded} (encoded) for transaction $transactionId" }
        } catch (e: Exception) {
            val message = "Failed to verify transaction's signature($signature) by ${signature.by.encoded} (encoded) for " +
                    "transaction ${transactionId}. Message: ${e.message}"
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
        verifySignature(transaction.id, signature)
        return transaction.addSignature(signature)
    }

    protected fun verifyTransaction(signedTransaction: ConsensualSignedTransactionInternal) {
        ConsensualTransactionMetadataVerifier(signedTransaction.wireTransaction.metadata).verify()
        ConsensualLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verify()
    }
}