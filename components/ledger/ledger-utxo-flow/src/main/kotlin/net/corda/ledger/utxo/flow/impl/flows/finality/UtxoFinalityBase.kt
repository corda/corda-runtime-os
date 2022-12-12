package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
abstract class UtxoFinalityBase : SubFlow<UtxoSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @Suspendable
    protected fun verifySignature(
        transactionId: SecureHash,
        signature: DigitalSignatureAndMetadata,
        onFailure: ((message: String) -> Unit)? = null
    ){
        try {
            log.debug("Verifying signature($signature) of transaction: $transactionId")
            transactionSignatureService.verifySignature(transactionId, signature)
        } catch (e: Exception) {
            val message =
                "Failed to verify transaction's signature($signature) from session: ${signature.by} for transaction " +
                        "${transactionId}. Message: ${e.message}"
            log.warn(message)
            if (onFailure != null)
                onFailure(message)
            throw e
        }
    }

    @Suspendable
    protected fun verifyAndAddSignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ):UtxoSignedTransactionInternal {
        verifySignature(transaction.id, signature)
        return transaction.addSignature(signature)
    }

}
