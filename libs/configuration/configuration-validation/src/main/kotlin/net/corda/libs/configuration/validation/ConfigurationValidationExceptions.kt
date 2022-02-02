package net.corda.libs.configuration.validation

import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig

class ConfigurationValidationException(val key: String, val config: SmartConfig, val errors: Set<String>) :
    RuntimeException(
        "Configuration failed to validate for key $key: ${errors.joinToString(",")}. Full config: ${
            config.toSafeConfig().root().render(
                ConfigRenderOptions.concise()
            )
        }"
    )

class ConfigurationSchemaFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)