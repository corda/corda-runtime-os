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

        val initialTransaction = session.receive<UtxoSignedTransactionInternal>()
        val transactionId = initialTransaction.id
        log.debug { "Beginning receive finality for transaction: $transactionId" }

        flowEngine.subFlow(TransactionBackchainResolutionFlow(initialTransaction, session))

        initialTransaction.signatures.forEach {
            verifySignature(transactionId, it) { message ->
                session.send(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(message)
                )
            }
        }

        verifyTransaction(initialTransaction)

        val myKeys = memberLookup.getMyLedgerKeys()
        var transaction = initialTransaction
        val signaturesPayload = if (validate(initialTransaction)) {
            log.trace { "Successfully validated transaction: $transactionId" }
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

            Payload.Success(mySignatures)
        } else {
            log.warn("Failed to validate transaction: $transactionId")

            persistenceService.persist(transaction, TransactionStatus.INVALID)
            log.debug { "Recorded transaction as invalid: $transactionId" }
            Payload.Failure("Transaction validation failed for transaction $transactionId when signature was requested")
        }

        log.trace { "Sending back our reply for transaction: $transactionId" }
        session.send(signaturesPayload)

        if (signaturesPayload is Payload.Failure) {
            throw CordaRuntimeException(signaturesPayload.message)
        }

        log.debug { "Waiting for other parties' signatures for transaction: $transactionId" }
        val otherPartiesSignatures = session.receive<List<DigitalSignatureAndMetadata>>()

        otherPartiesSignatures
            .filter { it !in transaction.signatures }
            .forEach {
                transaction = transaction.addSignature(it)
            }

        log.debug { "Verifying signatures of transaction: $transactionId" }
        transaction.verifySignatures()

        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)

        log.debug { "Waiting for Notary's signature for transaction: $transactionId" }
        val notarySignatures = session.receive<List<DigitalSignatureAndMetadata>>()
        if (notarySignatures.isEmpty()) {
            val message = "No notary signature received for transaction: $transactionId"
            log.warn(message)
            throw CordaRuntimeException(message)
        }
        log.debug { "Verifying and adding notary signatures for transaction: $transactionId" }
        notarySignatures.forEach {
            transaction = verifyAndAddNotarySignature(transaction, it)
        }

        val relevantStatesIndexes = transaction.getRelevantStatesIndexes(myKeys)
        persistenceService.persist(transaction, TransactionStatus.VERIFIED, relevantStatesIndexes)
        log.debug { "Recorded transaction with all parties' and the notary's signature $transactionId" }

        return transaction
    }

    @Suspendable
    private fun validate(signedTransaction: UtxoSignedTransaction): Boolean {
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
}
