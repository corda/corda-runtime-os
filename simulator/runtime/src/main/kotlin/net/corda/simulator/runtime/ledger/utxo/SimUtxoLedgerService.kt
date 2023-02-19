package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder

class SimUtxoLedgerService(
    member: MemberX500Name,
    private val fiber: SimFiber,
    private val configuration: SimulatorConfiguration,
    private val utxoTransactionBuilderFactory: UtxoTransactionBuilderFactory =
        utxoTransactionBuilderFactoryBase()
): UtxoLedgerService {

    private val signingService = fiber.createSigningService(member)
    private val notarySigningService = fiber.createNotarySigningService()
    private val persistenceService = fiber.getOrCreatePersistenceService(member)
    private val memberLookup = fiber.createMemberLookup(member)
    private val serializationService = BaseSerializationService()
    private val backchainHandler = TransactionBackchainHandlerBase(
        persistenceService, signingService, memberLookup, configuration)
    private val finalityHandler = UtxoTransactionFinalityHandler(
        memberLookup, signingService, notarySigningService, persistenceService, backchainHandler)

    override fun getTransactionBuilder(): UtxoTransactionBuilder {
        return utxoTransactionBuilderFactory.createUtxoTransactionBuilder(
            signingService, persistenceService, configuration)
    }

    override fun finalize(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): UtxoSignedTransaction {
        return finalityHandler.finalizeTransaction(signedTransaction, sessions)
    }

    override fun receiveFinality(session: FlowSession, validator: UtxoTransactionValidator): UtxoSignedTransaction {
        return finalityHandler.receiveFinality(session, validator)
    }

    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        return findSignedTransaction(id)?.toLedgerTransaction()
    }

    override fun findSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        val entity = persistenceService.find(UtxoTransactionEntity::class.java, String(id.bytes))?: return null
        return UtxoSignedTransactionBase
            .fromEntity(entity, signingService, serializationService, persistenceService, configuration)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        val result = persistenceService.query("UtxoTransactionOutputEntity.findUnconsumedStatesByType",
            UtxoTransactionOutputEntity::class.java)
            .setParameter("type", stateClass.canonicalName)
            .execute()
        val notaryInfo = fiber.getNotary()
        val notary = Party(notaryInfo.name, notaryInfo.publicKey)
        val stateAndRefs = result.map { utxoTransactionOutputEntity ->
            val stateRef = StateRef(SecureHash.parse(utxoTransactionOutputEntity.transactionId),
                utxoTransactionOutputEntity.index)
            val contractState = serializationService.deserialize<ContractState>(utxoTransactionOutputEntity.stateData)
            val encumbrance = serializationService
                .deserialize<List<EncumbranceGroupImpl>>(utxoTransactionOutputEntity.encumbranceData).firstOrNull()
            val transactionState = TransactionStateImpl(contractState as T, notary, encumbrance)
            StateAndRefImpl(transactionState, stateRef)
        }
        return stateAndRefs
    }

    override fun filterSignedTransaction(signedTransaction: UtxoSignedTransaction): UtxoFilteredTransactionBuilder {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        val serializer = BaseSerializationService()
        val notaryInfo = fiber.getNotary()
        val notary = Party(notaryInfo.name, notaryInfo.publicKey)
        return stateRefs.map { getStateAndRef(it, notary, serializer)}
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        return resolve<T>(listOf(stateRef)).first()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ContractState> getStateAndRef(
        stateRef: StateRef,
        notary: Party,
        serializer: BaseSerializationService
    ): StateAndRef<T> {
        val entity = persistenceService.find(
            UtxoTransactionOutputEntity::class.java,
            UtxoTransactionOutputEntityId(stateRef.transactionId.toString(), stateRef.index)
        ) ?: throw IllegalArgumentException("Cannot find transaction with transaction id: " +
                    String(stateRef.transactionId.bytes))

        val contractState = serializer.deserialize<ContractState>(entity.stateData)
        val encumbrance = serializer
            .deserialize<List<EncumbranceGroupImpl>>(entity.encumbranceData).firstOrNull()
        val transactionState = TransactionStateImpl(contractState as T, notary, encumbrance)
        return StateAndRefImpl(transactionState, stateRef)
    }
}