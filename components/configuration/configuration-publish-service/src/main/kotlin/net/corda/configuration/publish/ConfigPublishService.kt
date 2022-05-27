package net.corda.configuration.publish

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.dto.ConfigurationDto
import net.corda.lifecycle.Lifecycle

interface ConfigPublishService : Lifecycle {
    fun put(configDto: ConfigurationDto)

    fun bootstrapConfig(bootConfig: SmartConfig)
}