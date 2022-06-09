package net.corda.flow.service

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface FlowExecutor : Lifecycle {
    fun onConfigChange(config: Map<String, SmartConfig>)
}