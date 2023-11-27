package net.corda.libs.configuration.validation.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ExternalChannelsConfigValidator
import net.corda.schema.cordapp.configuration.ConfigKeys
import net.corda.v5.base.versioning.Version

class ExternalChannelsConfigValidatorImpl(private val configValidator: ConfigurationValidator) :
    ExternalChannelsConfigValidator {

    override fun validate(externalChannelsConfig: Collection<String>) {
        externalChannelsConfig.forEach {
            validate(it)
        }
    }

    override fun validate(externalChannelsConfig: String) {
        val smartConfig = SmartConfigFactory.createWithoutSecurityServices()
            .create(ConfigFactory.parseString(externalChannelsConfig))

        configValidator.validate(ConfigKeys.EXTERNAL_MESSAGING_CONFIG, Version(1, 0), smartConfig)
    }
}
