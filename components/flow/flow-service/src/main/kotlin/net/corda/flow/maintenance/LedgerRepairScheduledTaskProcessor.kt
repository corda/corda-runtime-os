package net.corda.flow.maintenance

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

// TODO Move this out of the `flow-service` module, may require dependency inversion to achieve this.
interface RecoverNotarizedTransactionsScheduledTaskProcessor : Lifecycle {
    fun onConfigChange(config: Map<String, SmartConfig>)
}

