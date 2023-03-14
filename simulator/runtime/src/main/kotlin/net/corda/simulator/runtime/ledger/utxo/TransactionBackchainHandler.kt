package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/***
 * This class is used to send backchain of an asset from one party to another
 */
class TransactionBackchainHandlerBase(
    val persistenceService: PersistenceService,
    val signingService: SigningService,
    val memberLookup: MemberLookup,
    val configuration: SimulatorConfiguration
):TransactionBackchainHandler {

    override fun sendBackChain(session: FlowSession) {
        val serializationService = BaseSerializationService()
        // Runs till all missing transaction have been requested
        while (true) {
            when (val request = session.receive(TransactionBackchainRequest::class.java)) {
                is TransactionBackchainRequest.Get -> {
                    // Find missing transaction entity. Used .map since corda does this to allow batching later
                    val transactions = request.transactionIds.map { id ->
                        persistenceService.find(UtxoTransactionEntity::class.java, String(id.bytes))
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    // Converts transaction entity to signed transaction and send to requesting party
                    transactions.map { session.send(listOf(
                        UtxoSignedTransactionBase.fromEntity(it,
                            signingService, serializationService, persistenceService, configuration)
                    )) }
                }

                is TransactionBackchainRequest.Stop -> return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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
                val retrievedTransactions = session.sendAndReceive(
                    List::class.java,
                    TransactionBackchainRequest.Get(setOf(transactionsToRetrieve.first()))
                ) as List<UtxoSignedTransaction>
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

    /**
     * Finds all dependent transaction of the provided transaction
     */
    private fun getTxDependencies(transaction: UtxoSignedTransaction) : Set<SecureHash> {
        return transaction.let { it.inputStateRefs.asSequence() + it.referenceStateRefs.asSequence() }
            .map { it.transactionId }
            .toSet()
    }
}

interface TransactionBackchainHandler{
    fun sendBackChain(session: FlowSession)
    fun receiveBackChain(transaction: UtxoSignedTransaction, session: FlowSession)
}

@CordaSerializable
sealed interface TransactionBackchainRequest {
    data class Get(val transactionIds: Set<SecureHash>): TransactionBackchainRequest
    object Stop: TransactionBackchainRequest
}