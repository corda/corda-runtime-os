package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
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

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        log.trace("Waiting for an initially signed transaction.")

        val initialTransaction = session.receive<UtxoSignedTransactionInternal>()
        val transactionId = initialTransaction.id
        log.debug("Initially signed transaction received: $transactionId")

        log.debug("Verifying transaction: $transactionId")

        log.debug("Verifying signatures of transaction: $transactionId")
        initialTransaction.signatures.forEach {
            verifySignature(transactionId, it) { message ->
                session.send(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(message)
                )
            }
        }

        // TODO [CORE-5982] Verify Transaction (platform/etc checks)
        log.debug("Verifying ledger transaction: $transactionId")
        verifyTransaction(initialTransaction)

        log.debug("Validating transaction: $transactionId")
        var transaction = initialTransaction
        val signaturesPayload = if (verify(initialTransaction)) {
            log.debug("Successfully validated transaction: $transactionId")
            // Which of our keys are required.
            val myExpectedSigningKeys = initialTransaction
                .getMissingSignatories()
                .intersect(
                    memberLookup
                        .myInfo()
                        .ledgerKeys
                        .toSet()
                )

            if (myExpectedSigningKeys.isEmpty()) {
                log.debug("We are not required signer of $transactionId.")
            }

            myExpectedSigningKeys.forEach{
                log.debug("Signing transaction: $transactionId with $it")
                transaction = transaction.sign(it).first
            }

            persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
            log.debug("Recorded transaction with the initial and our signatures: $transactionId")

            Payload.Success(transaction.signatures.filter { it !in initialTransaction.signatures })
        } else {
            log.warn("Failed to validate transaction: $transactionId")

            persistenceService.persist(transaction, TransactionStatus.INVALID)
            log.debug("Recorded transaction as invalid: $transactionId")
            Payload.Failure("Transaction validation failed for transaction $transactionId when signature was requested")
        }

        log.debug("Sending back our reply for transaction: $transactionId")
        session.send(signaturesPayload)

        if (signaturesPayload is Payload.Failure) {
            throw CordaRuntimeException(signaturesPayload.message)
        }

        log.debug("Waiting for other parties' signatures for transaction: $transactionId")
        val otherPartiesSignatures = session.receive<List<DigitalSignatureAndMetadata>>()

        // Q: Do we need to verify all signatures before adding them? verifySignatures() will check them again.
        otherPartiesSignatures
            .filter{ it !in transaction.signatures}
            .forEach {
                transaction = verifyAndAddSignature(transaction, it)
            }

        log.debug("Verifying signatures of transaction: $transactionId")
        transaction.verifySignatures()

        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        log.info("Recorded transaction with all parties' signatures $transactionId")

        log.debug("Waiting for Notary's signature for transaction: $transactionId")
        val notarySignatures = session.receive<List<DigitalSignatureAndMetadata>>()
        if (notarySignatures.isEmpty()) { // TODO reorg this if/when notarization is integrated
            log.warn("No notary signature received for transaction: $transactionId")
            // TODO error handling
        } else {
            log.debug("Verifying and adding notary signatures for transaction: $transactionId")
            notarySignatures.forEach {
                transaction = verifyAndAddSignature(transaction, it)
            }

            persistenceService.persist(transaction, TransactionStatus.VERIFIED)
            log.debug("Recorded transaction with all parties' and the notary's signature $transactionId")
        }

        // TODO Until we do not have notarisation, but still want to satisfy the smoke tests somehow...
        persistenceService.persist(transaction, TransactionStatus.VERIFIED)

        return transaction
    }

    @Suspendable
    private fun verify(signedTransaction: UtxoSignedTransaction): Boolean {
        return try {
            validator.checkTransaction(signedTransaction.toLedgerTransaction())
            true
        } catch (e: Exception) {
            // Should we only catch a specific exception type? Otherwise, some errors can be swallowed by this warning.
            // Means contracts can't use [check] or [require] unless we provide our own functions for this.
            if (e is IllegalStateException || e is IllegalArgumentException || e is CordaRuntimeException) {
                log.debug("Transaction ${signedTransaction.id} failed verification. Message: ${e.message}")
                false
            } else {
                throw e
            }
        }
    }

    private fun verifyTransaction(signedTransaction: UtxoSignedTransaction) {
        val ledgerTransactionToCheck = signedTransaction.toLedgerTransaction()
        val verifier = UtxoLedgerTransactionVerifier(ledgerTransactionToCheck)
        verifier.verifyPlatformChecks(signedTransaction.notary)
        verifier.verifyContracts()
    }
}