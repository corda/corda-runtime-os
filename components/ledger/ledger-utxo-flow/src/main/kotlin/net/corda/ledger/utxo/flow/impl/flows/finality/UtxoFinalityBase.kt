package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoTransactionMetadataVerifier
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
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

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

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

    @Suspendable
    protected fun verifyAndAddNotarySignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ):UtxoSignedTransactionInternal {
        try {
            log.debug { "Verifying signature($signature) of transaction: ${transaction.id}" }
            if (transaction.notary.owningKey != signature.by) {
                throw CordaRuntimeException("Notary's signature has not been created by the transaction's notary. " +
                    "Notary's public key: ${transaction.notary.owningKey} " +
                    "Notary signature's key: ${signature.by}"
                )
            }
            transactionSignatureService.verifyNotarySignature(transaction.id, signature)
        } catch (e: Exception) {
            val message =
                "Failed to verify transaction's signature($signature) from session: ${signature.by} for transaction " +
                        "${transaction.id}. Message: ${e.message}"
            log.warn(message)
            throw e
        }
        return transaction.addSignature(signature)
    }

    @Suspendable
    protected fun verifyTransaction(signedTransaction: UtxoSignedTransaction) {
        UtxoTransactionMetadataVerifier(signedTransaction.metadata).verify()
        val ledgerTransactionToCheck = signedTransaction.toLedgerTransaction()
        val verifier = UtxoLedgerTransactionVerifier(ledgerTransactionToCheck)
        verifier.verifyPlatformChecks(signedTransaction.notary)
        verifier.verifyContracts()
    }

}
