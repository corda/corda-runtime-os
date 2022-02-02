package net.corda.libs.configuration.validation

import net.corda.libs.configuration.SmartConfig

interface ConfigurationValidator {

    fun validate(key: String, config: SmartConfig)
}