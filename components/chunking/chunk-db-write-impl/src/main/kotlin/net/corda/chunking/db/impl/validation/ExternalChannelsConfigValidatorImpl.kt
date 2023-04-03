package net.corda.chunking.db.impl.validation

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.schema.cordapp.configuration.ConfigKeys
import net.corda.v5.base.versioning.Version

class ExternalChannelsConfigValidatorImpl(private val configValidator: ConfigurationValidator) :
    ExternalChannelsConfigValidator {

    override fun validate(cpksMetadata: Collection<CpkMetadata>) {
        cpksMetadata.forEach {
            validate(it.externalChannelsConfig)
        }
    }

    private fun validate(externalChannelsConfig: String?) {
        if (externalChannelsConfig == null) {
            return
        }

        val smartConfig = SmartConfigFactory.createWithoutSecurityServices()
            .create(ConfigFactory.parseString(externalChannelsConfig))

        configValidator.validate(ConfigKeys.EXTERNAL_MESSAGING_CONFIG, Version(1, 0), smartConfig)
    }
}
