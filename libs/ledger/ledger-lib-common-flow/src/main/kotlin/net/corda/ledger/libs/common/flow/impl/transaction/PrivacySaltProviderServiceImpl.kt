package net.corda.ledger.libs.common.flow.impl.transaction

import net.corda.flow.application.services.FlowCheckpointService
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.flow.transaction.PrivacySaltProviderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PrivacySaltProviderService::class])
class PrivacySaltProviderServiceImpl @Activate constructor(
    @Reference(service = FlowCheckpointService::class)
    private val flowCheckpointService: FlowCheckpointService
) : PrivacySaltProviderService {

    override fun generatePrivacySalt(): PrivacySalt {
        val flowCheckpoint = flowCheckpointService.getCheckpoint()
        val flowID = flowCheckpoint.flowId
        val suspendCount = flowCheckpoint.suspendCount
        val saltCounter = flowCheckpoint.ledgerSaltCounter
        val input = "$flowID-$suspendCount-$saltCounter"
        return PrivacySaltImpl(input.toByteArray())
    }
}
