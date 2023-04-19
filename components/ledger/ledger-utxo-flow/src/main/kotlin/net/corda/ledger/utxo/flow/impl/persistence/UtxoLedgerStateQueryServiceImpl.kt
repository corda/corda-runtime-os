package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsParameters
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ UtxoLedgerStateQueryService::class, UsedByFlow::class ],
    scope = PROTOTYPE
)
class UtxoLedgerStateQueryServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoLedgerStateQueryService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <T: ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindUnconsumedStatesByTypeExternalEventFactory::class.java,
                FindUnconsumedStatesByTypeParameters(stateClass)
            )
        }.map { it.toStateAndRef(serializationService)}
    }

    @Suspendable
    override fun resolveStateRefs(stateRefs: Iterable<StateRef>): List<StateAndRef<*>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                ResolveStateRefsExternalEventFactory::class.java,
                ResolveStateRefsParameters(stateRefs)
            )
        }.map { it.toStateAndRef<ContractState>(serializationService) }
    }
}