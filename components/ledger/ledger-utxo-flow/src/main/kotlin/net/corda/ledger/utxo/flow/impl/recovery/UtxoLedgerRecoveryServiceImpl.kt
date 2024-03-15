package net.corda.ledger.utxo.flow.impl.recovery

import net.corda.ledger.utxo.flow.impl.flows.recovery.UtxoLoggingRecoveryFlow
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.recovery.UtxoLedgerRecoveryService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.time.Duration
import java.time.Instant

@Component(service = [UtxoLedgerRecoveryService::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class UtxoLedgerRecoveryServiceImpl @Activate constructor(
    @Reference(service = FlowEngine::class) private val flowEngine: FlowEngine
) : UtxoLedgerRecoveryService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun recoverMissedNotarisedTransactions(from: Instant, until: Instant, duration: Duration) {
        flowEngine.subFlow(UtxoLoggingRecoveryFlow(from, until, duration))
    }
}