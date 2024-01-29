package net.corda.ledger.common.flow.impl.transaction

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.flow.transaction.PrivacySaltProviderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PrivacySaltProviderService::class])
class PrivacySaltProviderServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : PrivacySaltProviderService {

    override fun generatePrivacySalt(): PrivacySalt {
        val flowCheckpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        val flowID = flowCheckpoint.flowId
        val suspendCount = flowCheckpoint.suspendCount
        val saltCounter = flowCheckpoint.ledgerSaltCounter
        val input = "$flowID-$suspendCount-$saltCounter"
        return PrivacySaltImpl(input.toByteArray())
    }
}
