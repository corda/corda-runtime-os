package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.tools.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import java.security.PublicKey
import java.time.Instant

@Suppress("ForbiddenComment")
// TODO: inject clock for signature metadata timestamp
class SimConsensualLedgerService(
    private val signingService: SigningService,
    private val memberLookup: MemberLookup,
    private val configuration: SimulatorConfiguration,
    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory =
        consensualTransactionBuilderFactoryBase()
) : ConsensualLedgerService {

    override fun finalize(
        signedTransaction: ConsensualSignedTransaction,
        sessions: List<FlowSession>
    ): ConsensualSignedTransaction {

        val finalSignedTransaction = sessions.fold(signedTransaction) {
            tx, sess ->
                sess.send(signedTransaction)
            (tx as ConsensualSignedTransactionBase).addSignature(sess.receive<DigitalSignatureAndMetadata>())
        }

        sessions.forEach {
            it.send(finalSignedTransaction)
        }
        return finalSignedTransaction
    }

    override fun findLedgerTransaction(id: SecureHash): ConsensualLedgerTransaction? {
        TODO("Not yet implemented")
    }

    override fun findSignedTransaction(id: SecureHash): ConsensualSignedTransaction? {
        TODO("Not yet implemented")
    }

    override fun getTransactionBuilder(): ConsensualTransactionBuilder {
        return consensualTransactionBuilderFactory.createConsensualTxBuilder(
            signingService,
            memberLookup,
            configuration
        )
    }

    override fun receiveFinality(
        session: FlowSession,
        validator: ConsensualTransactionValidator
    ): ConsensualSignedTransaction {
        var signedTransaction = session.receive<ConsensualSignedTransaction>()
        validator.checkTransaction(signedTransaction.toLedgerTransaction())
        val signature = signStates(signedTransaction, memberLookup.myInfo().ledgerKeys[0])
        session.send(signature)
        return session.receive()
    }

    private fun signStates(
        signedTransaction: ConsensualSignedTransaction,
        publicKey: PublicKey
    ): DigitalSignatureAndMetadata {
        val serializer = SimpleJsonMarshallingService()
        val bytesToSign = serializer.format(signedTransaction.toLedgerTransaction().states).toByteArray()
        val signature = signingService.sign(bytesToSign, publicKey, SignatureSpec.ECDSA_SHA256)
        return DigitalSignatureAndMetadata(signature, DigitalSignatureMetadata(Instant.now(), mapOf()))
    }
}


