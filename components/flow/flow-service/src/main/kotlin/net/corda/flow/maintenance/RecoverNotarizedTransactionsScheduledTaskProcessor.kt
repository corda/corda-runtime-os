package net.corda.flow.maintenance

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface RecoverNotarizedTransactionsScheduledTaskProcessor : Lifecycle {
    fun onConfigChange(config: Map<String, SmartConfig>)
}

