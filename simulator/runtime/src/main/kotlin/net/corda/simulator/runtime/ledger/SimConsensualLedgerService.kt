package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.ConsensualTransactionEntity
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowSession
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
        transaction: ConsensualSignedTransaction,
        sessions: Iterable<FlowSession>
    ): ConsensualSignedTransaction {
        val distinctSessions = sessions.distinctBy { it.counterparty }

        @Suppress("unchecked_cast")
        val finalSignedTransaction = distinctSessions.fold(transaction) { tx, sess ->
            sess.send(transaction)
            (tx as ConsensualSignedTransactionBase)
                .addSignatures(sess.receive(List::class.java) as List<DigitalSignatureAndMetadata>)
        }

        distinctSessions.forEach { it.send(finalSignedTransaction) }
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
        val signedTransaction = session.receive(ConsensualSignedTransaction::class.java)
        validator.checkTransaction(signedTransaction.toLedgerTransaction())

        val keysToSignWith = memberLookup.myInfo().ledgerKeys.filter {
            signedTransaction.toLedgerTransaction().requiredSignatories.contains(it)
        }

        val signatures = signStates(signedTransaction, keysToSignWith)
        session.send(signatures)
        val finalizedTx = session.receive(ConsensualSignedTransactionBase::class.java)
        persistenceService.persist(finalizedTx.toEntity())
        return finalizedTx
    }

    private fun signStates(
        signedTransaction: ConsensualSignedTransaction,
        publicKeys: List<PublicKey>
    ): List<DigitalSignatureAndMetadata> {
        val serializer = SimpleJsonMarshallingService()
        val bytesToSign = serializer.format(signedTransaction.toLedgerTransaction().states).toByteArray()
        val signatures = publicKeys.map {
            val signature = signingService.sign(bytesToSign, it, SignatureSpec.ECDSA_SHA256)
            DigitalSignatureAndMetadata(
                signature,
                DigitalSignatureMetadata(Instant.now(), SignatureSpec("dummySignatureName"), mapOf())
            )
        }
        return signatures
    }
}


