package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
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
    private val member: MemberX500Name,
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
    private val transactionFinalizer = UtxoTransactionFinalizer(
        memberLookup, signingService, notarySigningService, persistenceService, configuration)

    override fun getTransactionBuilder(): UtxoTransactionBuilder {
        return utxoTransactionBuilderFactory.createUtxoTransactionBuilder(
            signingService, persistenceService, configuration)
    }

    override fun finalize(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): UtxoSignedTransaction {
        return transactionFinalizer.finalizeTransaction(signedTransaction, sessions)
    }

    override fun receiveFinality(session: FlowSession, validator: UtxoTransactionValidator): UtxoSignedTransaction {
        return transactionFinalizer.receiveFinality(session, validator)
    }

    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        return findSignedTransaction(id)?.toLedgerTransaction()
    }

    override fun findSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        val entity = getSignedTxEntity(id) ?: return null

        return UtxoSignedTransactionBase
            .fromEntity(entity, signingService, serializationService, persistenceService, configuration)
    }

    private fun getSignedTxEntity(id: SecureHash): UtxoTransactionEntity?{
        return persistenceService.query("UtxoTransactionEntity.findByTransactionId",
            UtxoTransactionEntity::class.java)
            .setParameter("transactionId", String(id.bytes))
            .execute().firstOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        val result = persistenceService.query("UtxoTransactionOutputEntity.findUnconsumedStatesByType",
            UtxoTransactionOutputEntity::class.java)
            .setParameter("type", stateClass.canonicalName)
            .execute()
        val stateAndRefs = result.map{ utxoTransactionOutputEntity ->
            val signedTxEntity = getSignedTxEntity(SecureHash.parse(utxoTransactionOutputEntity.transactionId))
                ?: throw IllegalArgumentException("Transaction not found for transaction id: " +
                        utxoTransactionOutputEntity.transactionId
                )

            val ledgerTx = UtxoLedgerTransactionBase(
                UtxoStateLedgerInfo(
                    serializationService.deserialize(signedTxEntity.commandData),
                    serializationService.deserialize(signedTxEntity.inputData),
                    serializationService.deserialize(signedTxEntity.notaryData),
                    serializationService.deserialize(signedTxEntity.referenceStateDate),
                    serializationService.deserialize(signedTxEntity.signatoriesDate),
                    serializationService.deserialize(signedTxEntity.timeWindowDate),
                    serializationService.deserialize(signedTxEntity.outputData),
                    serializationService.deserialize(signedTxEntity.attachmentData)
                ),
                listOf(),
                listOf()
            )
            val stateRef = StateRef(ledgerTx.id, utxoTransactionOutputEntity.index)
            val transactionState = TransactionStateImpl(
                ledgerTx.outputContractStates[utxoTransactionOutputEntity.index] as T, ledgerTx.ledgerInfo.notary,
                null)
            StateAndRefImpl(transactionState, stateRef)
        }
        return stateAndRefs
    }

    override fun filterSignedTransaction(signedTransaction: UtxoSignedTransaction): UtxoFilteredTransactionBuilder {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        TODO("Not yet implemented")
    }
}