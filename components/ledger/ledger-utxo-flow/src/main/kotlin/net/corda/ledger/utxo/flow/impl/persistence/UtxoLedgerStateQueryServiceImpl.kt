package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsParameters
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
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
    @Suppress("UNCHECKED_CAST")
    override fun <T: ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindUnconsumedStatesByTypeExternalEventFactory::class.java,
                FindUnconsumedStatesByTypeParameters(stateClass)
            )
        }.map {
            val info = serializationService.deserialize<UtxoOutputInfoComponent>(it.info)
            val contractState = serializationService.deserialize<ContractState>(it.data)
            StateAndRefImpl(
                state = TransactionStateImpl(contractState as T, info.notary, info.getEncumbranceGroup()),
                ref = StateRef(SecureHash.parse(it.transactionId), it.leafIndex)
            )
        }
    }

    @Suspendable
    override fun resolveStateRefs(stateRefs: List<StateRef>): List<StateAndRef<*>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                ResolveStateRefsExternalEventFactory::class.java,
                ResolveStateRefsParameters(stateRefs)
            )
        }.chunked(4).map {// CORE-9012 use proper response format
            val transactionId = it[0].array().decodeToString()
            val leafIndex = it[1].array().decodeToString().toInt()
            val info = serializationService.deserialize<UtxoOutputInfoComponent>(it[2].array())
            val contractState = serializationService.deserialize<ContractState>(it[3].array())
            StateAndRefImpl(
                state = TransactionStateImpl(contractState, info.notary, info.getEncumbranceGroup()),
                ref = StateRef(SecureHash.parse(transactionId), leafIndex)
            )
        }
    }

}
