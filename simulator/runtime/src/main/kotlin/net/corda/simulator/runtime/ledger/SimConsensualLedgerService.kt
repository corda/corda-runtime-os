package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.ConsensualTransactionEntity
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import java.security.PublicKey
import java.time.Instant

class SimConsensualLedgerService(
    private val member: MemberX500Name,
    private val fiber: SimFiber,
    private val configuration: SimulatorConfiguration,
    private val consensualTransactionBuilderFactory: ConsensualTransactionBuilderFactory =
        consensualTransactionBuilderFactoryBase()
) : ConsensualLedgerService {

    private val signingService = fiber.createSigningService(member)
    private val memberLookup = fiber.createMemberLookup(member)
    private val persistenceService = fiber.getOrCreatePersistenceService(member)
    private val serializationService = BaseSerializationService()

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
        persistenceService.persist((finalSignedTransaction as ConsensualSignedTransactionBase).toEntity())
        return finalSignedTransaction
    }

    override fun findLedgerTransaction(id: SecureHash): ConsensualLedgerTransaction? {
        return findSignedTransaction(id)?.toLedgerTransaction()
    }

    override fun findSignedTransaction(id: SecureHash): ConsensualSignedTransaction? {
        val persistenceService = fiber.getOrCreatePersistenceService(member)
        val entity = persistenceService.find(ConsensualTransactionEntity::class.java, String(id.bytes)) ?: return null
        return ConsensualSignedTransactionBase.fromEntity(entity, signingService, serializationService, configuration)
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
        val finalizedTx: ConsensualSignedTransactionBase = session.receive()
        persistenceService.persist(finalizedTx.toEntity())
        return finalizedTx
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


