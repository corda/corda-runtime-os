package net.corda.configuration.publish

import net.corda.libs.configuration.dto.ConfigurationDto

interface ConfigPublishService {
    fun put(configDto: ConfigurationDto)
}