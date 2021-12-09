package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface ConfigWriteService : Lifecycle {
    fun bootstrapConfig(config: SmartConfig, instanceId: Int)
}