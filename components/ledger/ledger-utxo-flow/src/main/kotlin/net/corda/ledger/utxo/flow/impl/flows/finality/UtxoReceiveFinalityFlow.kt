package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
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

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        // Receive the original signed Transaction with the initiator's signature
        /*REVERT*/log.info( "Waiting for an initially signed transaction" )
        val incomingTransaction = session.receive<UtxoSignedTransactionInternal>()
        var signedByUsTransaction = incomingTransaction
        val transactionId = incomingTransaction.id
        /*REVERT*/log.info( "Initially signed transaction received: $transactionId" )

        /*REVERT*/log.info( "Verifying incoming transaction: $transactionId" )
        // Verifications
        // TODO [CORE-5982] Verify Ledger Transaction
        // Verify already added signatures.
        /*REVERT*/log.info( "Verifying signatures of incoming transaction: $transactionId" )
        incomingTransaction.signatures.forEach {
            try {
                /*REVERT*/log.info( "Verifying signature of incoming transaction: $transactionId, signature: $it" )
                transactionSignatureService.verifySignature(transactionId, it)
            } catch (e: Exception) {
                val payload: Payload.Failure<List<DigitalSignatureAndMetadata>> =
                    Payload.Failure(
                        "Failed to verify incoming transactions's signature from ${session.counterparty} of $it for signed transaction " +
                                "${transactionId}. Message: ${e.message}"
                    )
                session.send(payload)
                log.warn(payload.message)
                throw CordaRuntimeException(payload.message)
            }
        }
        // Verify the transaction.
        /*REVERT*/log.info( "Verifying transaction: $transactionId" )
        verifyTransaction(incomingTransaction)
        // Sign the Transaction if we are happy with its content.
        /*REVERT*/log.info( "Validating if the transaction is acceptable: $transactionId" )
        val signaturesPayload = if (verify(incomingTransaction)) {
            /*REVERT*/log.info( "Successfully Validated. transaction is acceptable: $transactionId" )
            // We check which of our keys are required.
            val myExpectedSigningKeys = incomingTransaction
                .getMissingSignatories()
                .intersect(
                    memberLookup
                        .myInfo()
                        .ledgerKeys
                        .toSet()
                )
            /*REVERT*/log.info( "XXX2: $transactionId" )

            if (myExpectedSigningKeys.isEmpty()) {
                log.debug { "We are not required signer of $transactionId." }
            }

            // We sign the transaction with all of our keys which is required.
            myExpectedSigningKeys.forEach() {
                /*REVERT*/log.info( "Signing transaction: $transactionId with $it" )
                signedByUsTransaction = signedByUsTransaction.sign(it).first
            }
            // Save the transaction with our signatures appended
            /*REVERT*/log.info( "Recording transaction with the initial and our signatures: $transactionId" )
            persistenceService.persist(signedByUsTransaction, TransactionStatus.UNVERIFIED)
            /*REVERT*/log.info( "Recorded transaction with the initial and our signatures: $transactionId" )

            Payload.Success(signedByUsTransaction.signatures.filter { it !in incomingTransaction.signatures })
        } else {
            // Save the incoming transaction as invalid
            persistenceService.persist(incomingTransaction, TransactionStatus.INVALID)
            Payload.Failure("Transaction verification failed for transaction $transactionId when signature was requested")
        }

        // Send back the new signatures or a Failure.
        /*REVERT*/log.info( "Sending back our reply for transaction: $transactionId" )
        session.send(signaturesPayload)

        if (signaturesPayload is Payload.Failure) {
            throw CordaRuntimeException(signaturesPayload.message)
        }

        // Receive the other parties signatures, if there are any
        /*REVERT*/log.info( "Waiting for other parties's signatures for transaction: $transactionId" )
        var signedByAllPartiesTransaction = signedByUsTransaction
        val otherPartiesSignatures = session.receive<List<DigitalSignatureAndMetadata>>()
        if (otherPartiesSignatures.isNotEmpty()) {
            otherPartiesSignatures.forEach {
                signedByAllPartiesTransaction = signedByAllPartiesTransaction.addSignature(it)
            }

            /*REVERT*/log.info( "Verifying signatures of transaction: $transactionId" )
            signedByAllPartiesTransaction.verifySignatures()

            // Save the transaction with the new signatures.
            persistenceService.persist(signedByAllPartiesTransaction, TransactionStatus.UNVERIFIED)
            /*REVERT*/log.info( "Recorded transaction with all parties's signatures $transactionId" )
        }

        /*REVERT*/log.info( "Waiting for Notary's signature for transaction: $transactionId" )
        var signedByNotaryTransaction = signedByAllPartiesTransaction
        val notarySignatures = session.receive<List<DigitalSignatureAndMetadata>>()
        if (notarySignatures.isEmpty()) {
            log.warn( "No notary signature received for transaction: $transactionId" )
            // TODO error handling
        }
        if (notarySignatures.isNotEmpty()) {    // TODO remove this if when notarization is integrated
            /*REVERT*/log.debug( "Verifying notary signatures for transaction: $transactionId" )
            notarySignatures.forEach {
                try {
                    /*REVERT*/log.debug( "Verifying notary signature($it) for transaction: $transactionId" )
                    transactionSignatureService.verifySignature(transactionId, it)
                } catch (e: Exception) {
                    log.warn(
                        "Failed to verify notary signature from ${session.counterparty} of $it for signed transaction " +
                                "${transactionId}. Message: ${e.message}"
                    )

                    throw e
                }
            }

            notarySignatures.forEach {
                signedByNotaryTransaction = signedByNotaryTransaction.addSignature(it)
            }

            // Save the transaction with the new notary signatures.
            persistenceService.persist(signedByNotaryTransaction, TransactionStatus.VERIFIED)
            /*REVERT*/log.debug( "Recorded transaction with all parties's and the notary's signature $transactionId" )
        }

        return signedByNotaryTransaction
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
