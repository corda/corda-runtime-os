package net.corda.libs.configuration.validation.integration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.osgi.framework.FrameworkUtil

// Demonstrate validating configuration in an OSGi environment
class ConfigurationValidationIntegrationTest {

    companion object {
        private const val MESSAGING_CONFIG_FILE = "messaging-config-example.conf"
    }

    @Test
    fun `validate some messaging config`() {
        val validator = ConfigurationValidatorFactory.getConfigValidator()
        val config = loadConfig(MESSAGING_CONFIG_FILE)
        try {
            validator.validate(MESSAGING_CONFIG, config)
        } catch (e: ConfigurationValidationException) {
            assertEquals(MESSAGING_CONFIG, e.key)
            assertEquals(2, e.errors.size)
        }
    }

    private fun loadConfig(resource: String): SmartConfig {
        val url =
            FrameworkUtil.getBundle(this::class.java).getResource(resource) ?: this::class.java.classLoader.getResource(
                resource
            )
            ?: throw IllegalArgumentException("Failed to find $resource")
        val confStr = url.openStream().bufferedReader().readText()
        return SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(confStr))
    }
}