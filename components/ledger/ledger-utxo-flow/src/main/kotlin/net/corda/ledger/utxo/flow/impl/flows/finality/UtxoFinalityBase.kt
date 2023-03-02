package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.debug
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.containsAny
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import java.security.InvalidParameterException

/**
 * Initiator will notify the receiver side with FATAL if the notarization error cannot be recovered and the
 * transaction can be updated to INVALID.
 */
enum class FinalityNotarizationFailureType(val value: String) {
    FATAL("F"),
    UNKNOWN("U");

    companion object {
        fun String.toFinalityNotarizationFailureType() = when {
            this.equals(FATAL.value, ignoreCase = true) -> FATAL
            this.equals(UNKNOWN.value, ignoreCase = true) -> UNKNOWN
            else -> throw InvalidParameterException("FinalityNotarizationFailureType '$this' is not supported")
        }
    }

}

@CordaSystemFlow
abstract class UtxoFinalityBase : SubFlow<UtxoSignedTransaction> {

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var transactionVerificationService: UtxoLedgerTransactionVerificationService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

    abstract val log: Logger

    @Suspendable
    protected fun verifySignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata,
        sessionToNotify: FlowSession? = null
    ) {
        try {
            log.debug { "Verifying signature($signature) of transaction: $transaction.id" }
            transactionSignatureService.verifySignature(transaction, signature)
            log.debug { "Successfully verified signature($signature) by ${signature.by.encoded} (encoded) for transaction $transaction.id" }
        } catch (e: Exception) {
            val message = "Failed to verify transaction's signature($signature) by ${signature.by.encoded} (encoded) for " +
                    "transaction ${transaction.id}. Message: ${e.message}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            sessionToNotify?.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            throw e
        }
    }

    @Suspendable
    protected fun verifyAndAddSignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ): UtxoSignedTransactionInternal {
        verifySignature(transaction, signature)
        return transaction.addSignature(signature)
    }

    @Suspendable
    protected fun verifyAndAddNotarySignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ): UtxoSignedTransactionInternal {
        try {
            // If the notary service key (composite key) is provided we need to make sure it contains the key the
            // transaction was signed with. This means it was signed with one of the notary VNodes (worker).
            if (!KeyUtils.containsAny(transaction.notary.owningKey, listOf(signature.by))) {
                throw CordaRuntimeException(
                    "Notary's signature has not been created by the transaction's notary. " +
                            "Notary's public key: ${transaction.notary.owningKey} " +
                            "Notary signature's key: ${signature.by}"
                )
            }
            transactionSignatureService.verifySignature(transaction, signature)
            log.debug {
                "Successfully verified signature($signature) by notary ${transaction.notary} for transaction ${transaction.id}"
            }
        } catch (e: Exception) {
            val message ="Failed to verify transaction's signature($signature) by notary ${transaction.notary} for " +
                    "transaction ${transaction.id}. Message: ${e.message}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            throw e
        }
        return transaction.addSignature(signature)
    }

    @Suspendable
    protected fun verifyTransaction(signedTransaction: UtxoSignedTransaction) {
        try {
            transactionVerificationService.verify(signedTransaction.toLedgerTransaction())
        } catch(e: Exception){
            persistInvalidTransaction(signedTransaction)
            throw e
        }
    }

    @Suspendable
    protected fun persistInvalidTransaction(transaction: UtxoSignedTransaction) {
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.debug { "Recorded transaction as invalid: ${transaction.id}" }
    }

    @Suspendable
    protected fun verifyExistingSignatures(
        initialTransaction: UtxoSignedTransactionInternal,
        sessionToNotify: FlowSession? = null
    ) {
        if (initialTransaction.signatures.isEmpty()){
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
