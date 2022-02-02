package net.corda.libs.configuration.validation.integration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// Demonstrate validating configuration in an OSGi environment
class ConfigurationValidationIntegrationTest {

    companion object {
        private const val MESSAGING_CONFIG_EXAMPLE = """
            {
                "bus": {
                    "busType": "KAFKA",
                    "kafkaProperties": {
                        "bootstrap.servers": "localhost:9092"
                    }
                },
                "subscription": {
                    "poll.timeout": -1
                },
                "publisher": {
                    "close.timeout": -1
                }
            }
        """
    }

    @Test
    fun `validate some messaging config`() {
        val validator = ConfigurationValidatorFactory().getConfigValidator()
        val config = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(
            MESSAGING_CONFIG_EXAMPLE
        ))
        try {
            validator.validate(MESSAGING_CONFIG, config)
        } catch (e: ConfigurationValidationException) {
            assertEquals(MESSAGING_CONFIG, e.key)
            assertEquals(2, e.errors.size)
        }
    }
}