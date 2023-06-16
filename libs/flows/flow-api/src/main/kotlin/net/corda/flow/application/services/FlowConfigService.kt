package net.corda.flow.application.services

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
interface FlowConfigService {
    fun getFlowConfigs(): Map<String, SmartConfig>
    fun getLedgerConfig(): SmartConfig?
}
