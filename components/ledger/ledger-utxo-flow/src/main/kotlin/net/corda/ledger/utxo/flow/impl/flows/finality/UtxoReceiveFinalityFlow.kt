package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
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
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator

@CordaSystemFlow
class UtxoReceiveFinalityFlow(
    private val session: FlowSession,
    private val validator: UtxoTransactionValidator
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val signedTransaction = session.receive<UtxoSignedTransactionInternal>()
        val transactionId = signedTransaction.id

        // TODO [CORE-5982] Verify Ledger Transaction

        // Verify the transaction.
        verifyTransaction(signedTransaction)

        // TODO [CORE-5982] Verify already added signatures.
        val myKeys = memberLookup
            .myInfo()
            .ledgerKeys
            .toSet()
        val signaturesPayload = if (verify(signedTransaction)) {
            persistenceService.persist(signedTransaction, TransactionStatus.UNVERIFIED)

            // We check which of our keys are required.
            val myExpectedSigningKeys = signedTransaction
                .getMissingSignatories()
                .intersect(myKeys)

            if (myExpectedSigningKeys.isEmpty()) {
                log.debug { "We are not required signer of $transactionId." }
            }

            // We sign the transaction with all of our keys which is required.
            val newSignatures = myExpectedSigningKeys.map {
                signedTransaction.sign(it).second
            }

            Payload.Success(newSignatures)
        } else {
            Payload.Failure("Transaction verification failed for transaction $transactionId when signature was requested")
        }

        session.send(signaturesPayload)

        if (signaturesPayload is Payload.Failure) {
            throw CordaRuntimeException(signaturesPayload.message)
        }

        val signedTransactionToFinalize = session.receive<UtxoSignedTransactionInternal>()

        // A [require] block isn't the correct option if we want to do something with the error on the peer side
        require(signedTransactionToFinalize.id == transactionId) {
            "Expected to received transaction $transactionId from ${session.counterparty} to finalise but received " +
                    "${signedTransactionToFinalize.id} instead"
        }

        signedTransactionToFinalize.verifySignatures()

        val relevantStatesIndexes = signedTransactionToFinalize.outputStateAndRefs.withIndex().filter { (_, stateAndRef) ->
            Contract.isRelevant(stateAndRef.state.contractState, myKeys)
        }.map { it.index }

        persistenceService.persist(signedTransactionToFinalize, TransactionStatus.VERIFIED, relevantStatesIndexes)
        log.debug { "Recorded signed transaction $transactionId" }

        session.send(Unit)
        log.trace { "Sent acknowledgement to initiator of finality for signed transaction ${signedTransactionToFinalize.id}" }

        return signedTransactionToFinalize
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
                log.debug { "Transaction ${signedTransaction.id} failed verification. Message: ${e.message}" }
                false
            } else {
                throw e
            }
        }
    }

    private fun verifyTransaction(signedTransaction: UtxoSignedTransaction) {
        val ledgerTransactionToCheck = signedTransaction.toLedgerTransaction()
        UtxoLedgerTransactionVerifier(ledgerTransactionToCheck).verify()
    }
}