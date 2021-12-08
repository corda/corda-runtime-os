package net.corda.processors.db.internal.config.writeservice

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface ConfigWriteService : Lifecycle {
    fun bootstrapConfig(config: SmartConfig, instanceId: Int)
}