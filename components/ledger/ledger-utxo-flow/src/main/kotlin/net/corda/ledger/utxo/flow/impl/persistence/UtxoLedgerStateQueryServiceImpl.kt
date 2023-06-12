package net.corda.ledger.utxo.flow.impl.persistence

import io.micrometer.core.instrument.Timer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.FindUnconsumedStatesByType
import net.corda.ledger.utxo.flow.impl.persistence.LedgerPersistenceMetricOperationName.ResolveStateRefs
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ResolveStateRefsParameters
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
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
    service = [UtxoLedgerStateQueryService::class, UsedByFlow::class],
    scope = PROTOTYPE
)
class UtxoLedgerStateQueryServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoLedgerStateQueryService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <T : ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(FindUnconsumedStatesByType) }) @Suspendable {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindUnconsumedStatesByTypeExternalEventFactory::class.java,
                    FindUnconsumedStatesByTypeParameters(stateClass)
                )
            }.map { it.toStateAndRef(serializationService) }
        }
    }

    @Suspendable
    override fun resolveStateRefs(stateRefs: Iterable<StateRef>): List<StateAndRef<*>> {
        return recordSuspendable({ ledgerPersistenceFlowTimer(ResolveStateRefs) }) @Suspendable {
            if (stateRefs.count() == 0) {
                emptyList()
            } else {
                wrapWithPersistenceException {
                    externalEventExecutor.execute(
                        ResolveStateRefsExternalEventFactory::class.java,
                        ResolveStateRefsParameters(stateRefs)
                    )
                }.map { it.toStateAndRef<ContractState>(serializationService) }
            }
        }
    }

    private fun ledgerPersistenceFlowTimer(operationName: LedgerPersistenceMetricOperationName): Timer {
        return CordaMetrics.Metric.Ledger.PersistenceFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, operationName.name)
            .build()
    }
}