package net.corda.ledger.common.flow.impl.transaction

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
) {
    private fun generatePrivacySalt(flowID: String, suspendCount: String): PrivacySalt {
        val input = flowID + suspendCount
        return PrivacySaltImpl(input.toByteArray())
    }
}