package net.corda.ledger.consensual.impl.flows.finality

import net.corda.ledger.common.internal.transaction.SignableData
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransactionVerifier

class ConsensualReceiveFinalityFlow(
    private val session: FlowSession,
    private val verifier: ConsensualSignedTransactionVerifier
) : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {
        val signedTransaction = session.receive<ConsensualSignedTransaction>()
        val transactionId = signedTransaction.id

        // TODO Verify Ledger Transaction
        // TODO Verify already added signatures.

        // TODO [CORE-7027] Catch exceptions coming out of this method and return a [Try] structure to [ConsensualFinalityFlow]
        verifier.verify(signedTransaction)

        // TODO [CORE-7029] Record unfinalised transaction

        val (_, mySignature) = signedTransaction.addSignature(memberLookup.myInfo().ledgerKeys.first())

        session.send(mySignature)

        val signedTransactionToFinalize = session.receive<ConsensualSignedTransaction>()

        // A [require] block isn't the correct option if we want to do something with the error on the peer side
        require(signedTransactionToFinalize.id == transactionId) {
            "Expected to received transaction $transactionId from ${session.counterparty} to finalise but received " +
                    "${signedTransaction.id} instead"
        }

        signedTransactionToFinalize.verifySignatureValidity()

        // TODO [CORE-7055] Record the transaction
        log.debug { "Recorded signed transaction $transactionId" }

        session.send(Unit)
        log.trace { "Sent acknowledgement to initiator of finality for signed transaction ${signedTransactionToFinalize.id}" }

        return signedTransactionToFinalize
    }
}