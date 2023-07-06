package net.corda.flow.application.services

import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
interface FlowConfigService {

    /**
     * Retrieves all the available configs from the flow context.
     */
    fun getFlowConfigs(): Map<String, SmartConfig>

    /**
     * Retrieves the ledger config from the flow context if available.
     */
    fun getLedgerConfig(): SmartConfig?
}
