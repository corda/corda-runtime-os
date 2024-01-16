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
        val flowID = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowId
        val suspendCount = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.suspendCount
        val input = (flowID + suspendCount).repeat(10)
        return PrivacySaltImpl(input.toByteArray())
    }
}
