package net.corda.flow.application.config.impl

import net.corda.flow.application.services.FlowConfigService
import net.corda.flow.fiber.FlowFiberService
import net.corda.libs.configuration.SmartConfig
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.schema.configuration.ConfigKeys.UTXO_LEDGER_CONFIG
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ FlowConfigService::class, UsedByFlow::class ],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class FlowConfigServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowConfigService, UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {
    override fun getFlowConfigs(): Map<String, SmartConfig> {
        return flowFiberService.getExecutingFiber().getExecutionContext().configs
    }

    override fun getLedgerConfig(): SmartConfig? {
        return getFlowConfigs()[UTXO_LEDGER_CONFIG]
    }
}