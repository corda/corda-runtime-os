package net.corda.ledger.utxo.flow.impl.repair

import net.corda.ledger.utxo.flow.impl.flows.repair.UtxoLoggingLedgerRepairFlow
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.repair.UtxoLedgerRepairService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.time.Duration
import java.time.Instant

@Component(service = [UtxoLedgerRepairService::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class UtxoLedgerRepairServiceImpl @Activate constructor(
    @Reference(service = FlowEngine::class) private val flowEngine: FlowEngine
) : UtxoLedgerRepairService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun repairTransactions(from: Instant, until: Instant, duration: Duration) {
        flowEngine.subFlow(UtxoLoggingLedgerRepairFlow(from, until, duration))
    }
}
