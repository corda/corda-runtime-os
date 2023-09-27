package net.corda.simulator.runtime.ledger.utxo


import net.corda.crypto.core.bytes
import net.corda.crypto.core.parseSecureHash
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.FinalizationResult
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.query.VaultNamedParameterizedQuery
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import net.corda.v5.membership.NotaryInfo

/**
 * Simulator implementation of [UtxoLedgerService]
 */
@Suppress("TooManyFunctions")
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
    private val notaryLookup = fiber.createNotaryLookup()
    private val serializationService = BaseSerializationService()
    private val backchainHandler = TransactionBackchainHandlerBase(
        persistenceService, signingService, memberLookup, configuration, fiber.getNotary())
    private val finalityHandler = UtxoTransactionFinalityHandler(
        memberLookup, signingService, notarySigningService, persistenceService, backchainHandler)

    override fun createTransactionBuilder(): UtxoTransactionBuilder {
        return utxoTransactionBuilderFactory.createUtxoTransactionBuilder(
            signingService, persistenceService, configuration, notaryLookup)
    }

    override fun finalize(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): FinalizationResult {
        return SimFinalizationResultImpl(finalityHandler.finalizeTransaction(signedTransaction, sessions))
    }

    override fun receiveFinality(session: FlowSession, validator: UtxoTransactionValidator): FinalizationResult {
        return SimFinalizationResultImpl(finalityHandler.receiveFinality(session, validator))
    }

    override fun sendAndReceiveTransactionBuilder(
        transactionBuilder: UtxoTransactionBuilder,
        session: FlowSession
    ): UtxoTransactionBuilder {
        TODO("Not yet implemented")
    }

    override fun receiveTransactionBuilder(session: FlowSession): UtxoTransactionBuilder {
        TODO("Not yet implemented")
    }

    override fun sendUpdatedTransactionBuilder(transactionBuilder: UtxoTransactionBuilder, session: FlowSession) {
        TODO("Not yet implemented")
    }

    override fun <R : Any?> query(queryName: String, resultClass: Class<R>): VaultNamedParameterizedQuery<R> {
        TODO("Not yet implemented")
    }

    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        return findSignedTransaction(id)?.toLedgerTransaction()
    }

    override fun findSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        val entity = persistenceService.find(UtxoTransactionEntity::class.java, String(id.bytes))?: return null
        return UtxoSignedTransactionBase
            .fromEntity(entity, fiber.getNotary(), signingService, serializationService, persistenceService, configuration)
    }

    override fun filterSignedTransaction(signedTransaction: UtxoSignedTransaction): UtxoFilteredTransactionBuilder {
        return UtxoFilteredTransactionBuilderBase(signedTransaction as UtxoSignedTransactionBase)
    }

    /**
     * Searches for unconsumed state of a particular [ContractState] type. Uses Persistence Service to filter
     * [UtxoTransactionOutputEntity] based on [ContractState] type. Converts the entities fetched into [StateAndRef]
     * by deserializing the contractStateData and encumbranceData
     *
     * @param type The [ContractState] type to filter unconsumed states
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ContractState> findUnconsumedStatesByType(type: Class<T>): List<StateAndRef<T>> {
        // Fetch unconsumed states using named query
        val result = persistenceService.query("UtxoTransactionOutputEntity.findUnconsumedStatesByType",
            UtxoTransactionOutputEntity::class.java)
            .setParameter("type", type.canonicalName)
            .execute()
        val notaryInfo = fiber.getNotary()

        // For each entity fetched, convert it to StateAndRef
        val stateAndRefs = result.results.map { utxoTransactionOutputEntity ->
            val stateRef = StateRef(
                parseSecureHash(utxoTransactionOutputEntity.transactionId),
                utxoTransactionOutputEntity.index)
            val contractState = serializationService.deserialize(
                utxoTransactionOutputEntity.stateData, ContractState::class.java)
            val encumbrance = serializationService
                .deserialize(utxoTransactionOutputEntity.encumbranceData, List::class.java).firstOrNull()
            val transactionState = SimTransactionState(contractState as T,
                notaryInfo.name, notaryInfo.publicKey, encumbrance as? EncumbranceGroup)
            SimStateAndRef(transactionState, stateRef)
        }
        return stateAndRefs
    }

    override fun <T : ContractState> findUnconsumedStatesByExactType(type: Class<T>): List<StateAndRef<T>> {
        TODO("Not implemented yet")
    }

    /**
     * Resolves [StateRef] list to [StateAndRef] list
     */
    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        val serializer = BaseSerializationService()
        val notaryInfo = fiber.getNotary()
        return stateRefs.map { getStateAndRef(it, notaryInfo, serializer)}
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        return resolve<T>(listOf(stateRef)).first()
    }

    /**
     * Converts [StateRef] to [StateAndRef]. Uses Persistence Service to filter
     * [UtxoTransactionOutputEntity] based on txId and index from [StateRef]. Converts the entities fetched into
     * [StateAndRef] by deserializing the contractStateData and encumbranceData
     *
     * @param stateRef The [StateRef] to be converted to [StateAndRef]
     * @param notary The notary to be added to [TransactionState]
     * @param serializer The serialization service to deserialize contractState and encumbrance
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : ContractState> getStateAndRef(
        stateRef: StateRef,
        notaryInfo: NotaryInfo,
        serializer: BaseSerializationService
    ): StateAndRef<T> {
        //Fetch output state based in txid
        val entity = persistenceService.find(
            UtxoTransactionOutputEntity::class.java,
            UtxoTransactionOutputEntityId(stateRef.transactionId.toString(), stateRef.index)
        ) ?: throw IllegalArgumentException("Cannot find transaction with transaction id: " +
                    String(stateRef.transactionId.bytes) + " and index" + stateRef.index)

        //Convert entity to StateAndRef
        val contractState = serializer.deserialize(entity.stateData, ContractState::class.java)
        val encumbrance = serializer
            .deserialize(entity.encumbranceData, List::class.java).firstOrNull()
        val transactionState = SimTransactionState(contractState as T,
            notaryInfo.name, notaryInfo.publicKey, encumbrance as? EncumbranceGroup)
        return SimStateAndRef(transactionState, stateRef)
    }
}