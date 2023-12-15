package net.corda.ledger.utxo.flow.impl

import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [UtxoLedgerMetricRecorder::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class UtxoLedgerMetricRecorderImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext
) : UtxoLedgerMetricRecorder, UsedByFlow, SingletonSerializeAsToken {

    override fun recordTransactionBackchainLength(length: Int) {
        CordaMetrics.Metric.Ledger.BackchainResolutionChainLength
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .build()
            .record(length.toDouble())
    }
}
