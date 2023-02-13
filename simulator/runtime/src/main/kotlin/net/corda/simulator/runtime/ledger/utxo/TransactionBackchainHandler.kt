package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class TransactionBackchainHandlerBase(
    val persistenceService: PersistenceService,
    val signingService: SigningService,
    val memberLookup: MemberLookup,
    val configuration: SimulatorConfiguration
):TransactionBackchainHandler {

    override fun sendBackChain(session: FlowSession) {
        val serializationService = BaseSerializationService()
        while (true) {
            when (val request = session.receive<TransactionBackchainRequest>()) {
                is TransactionBackchainRequest.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        persistenceService.find(UtxoTransactionEntity::class.java, String(id.bytes))
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    transactions.map { session.send(listOf(
                        UtxoSignedTransactionBase.fromEntity(it,
                            signingService, serializationService, persistenceService, configuration)
                    )) }
                }

                is TransactionBackchainRequest.Stop -> return
            }
        }
    }

    override fun receiveBackChain(transaction: UtxoSignedTransaction, session: FlowSession) {
        val dependencies = getTxDependencies(transaction)
        val availableTx = dependencies.filter {
            persistenceService.find(UtxoTransactionEntity::class.java, String(it.bytes)) != null
        }.toSet()
        val originalTransactionsToRetrieve = dependencies - availableTx
        val sortedTransactionIds = TopologicalSort()
        val transactionsToRetrieve = LinkedHashSet(originalTransactionsToRetrieve)
        if(transactionsToRetrieve.isNotEmpty()){
            while (transactionsToRetrieve.isNotEmpty()){
                val retrievedTransactions = session.sendAndReceive<List<UtxoSignedTransaction>>(
                    TransactionBackchainRequest.Get(setOf(transactionsToRetrieve.first()))
                )
                for (retrievedTransaction in retrievedTransactions) {
                    val retrievedTransactionId = retrievedTransaction.id
                    persistenceService.persist((retrievedTransaction as UtxoSignedTransactionBase).toEntity())
                    persistenceService.persist(retrievedTransaction.toOutputsEntity(memberLookup.myInfo().ledgerKeys.toSet()))
                    transactionsToRetrieve.remove(retrievedTransactionId)
                    addUnseenDependenciesToRetrieve(retrievedTransaction, sortedTransactionIds, transactionsToRetrieve)
                }
            }
            if (sortedTransactionIds.isNotEmpty()) {
                session.send(TransactionBackchainRequest.Stop)
            }
        }else{
            session.send(TransactionBackchainRequest.Stop)
        }
    }

    private fun addUnseenDependenciesToRetrieve(
        retrievedTransaction: UtxoSignedTransaction,
        sortedTransactionIds: TopologicalSort,
        transactionsToRetrieve: LinkedHashSet<SecureHash>
    ){
        if (retrievedTransaction.id !in sortedTransactionIds.transactionIds) {
            getTxDependencies(retrievedTransaction).let { dependencies ->
                val unseenDependencies = dependencies - sortedTransactionIds.transactionIds

                sortedTransactionIds.add(retrievedTransaction.id, unseenDependencies)
                transactionsToRetrieve.addAll(unseenDependencies)
            }
        }
    }

    private fun getTxDependencies(transaction: UtxoSignedTransaction) : Set<SecureHash> {
        return transaction.let { it.inputStateRefs.asSequence() + it.referenceStateRefs.asSequence() }
            .map { it.transactionHash }
            .toSet()
    }
}

interface TransactionBackchainHandler{
    fun sendBackChain(session: FlowSession)
    fun receiveBackChain(transaction: UtxoSignedTransaction, session: FlowSession)
}