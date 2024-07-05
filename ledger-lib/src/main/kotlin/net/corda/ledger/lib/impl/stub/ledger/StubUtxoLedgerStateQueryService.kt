package net.corda.ledger.lib.impl.stub.ledger

import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.utxo.flow.impl.cache.StateAndRefCache
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import javax.persistence.EntityManagerFactory

class StubUtxoLedgerStateQueryService(
    private val entityManagerFactory: EntityManagerFactory,
    private val serializationService: SerializationService,
    private val utxoRepository: UtxoRepository,
    private val stateAndRefCache: StateAndRefCache
) : UtxoLedgerStateQueryService {

    override fun <T : ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return entityManagerFactory.createEntityManager().transaction { em ->
            utxoRepository.findUnconsumedVisibleStatesByType(em).filter {
                val contractState = serializationService.deserialize<ContractState>(it.data)
                stateClass.isInstance(contractState)
            }.map { it.toStateAndRef(serializationService) }
        }
    }

    override fun resolveStateRefs(stateRefs: Iterable<StateRef>): List<StateAndRef<*>> {
        return if (stateRefs.count() == 0) {
            emptyList()
        } else {
            val cachedStateAndRefs = stateAndRefCache.get(stateRefs.toSet())
            val nonCachedStateRefs = stateRefs - cachedStateAndRefs.keys

            if (nonCachedStateRefs.isNotEmpty()) {
                val resolvedStateRefs = entityManagerFactory.createEntityManager().transaction { em ->
                    utxoRepository.resolveStateRefs(em, nonCachedStateRefs).map {
                        it.toStateAndRef<ContractState>(serializationService)
                    }
                }

                stateAndRefCache.putAll(resolvedStateRefs)
                cachedStateAndRefs.values + resolvedStateRefs
            } else {
                cachedStateAndRefs.values.toList()
            }
        }
    }
}