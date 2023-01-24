package net.corda.libs.configuration.validation.integration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.schema.configuration.ConfigKeys.P2P_LINK_MANAGER_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ConfigKeys.REST_CONFIG
import net.corda.schema.configuration.ConfigKeys.SANDBOX_CONFIG
import net.corda.schema.configuration.ConfigKeys.SECRETS_CONFIG
import net.corda.schema.configuration.MessagingConfig
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

// Demonstrate validating configuration in an OSGi environment
@ExtendWith(ServiceExtension::class)
class ConfigurationValidationIntegrationTest {

    companion object {
        private const val MESSAGING_CONFIG_FILE = "messaging-config-example.conf"
        private const val VALID_MESSAGING_CONFIG_FILE = "messaging-config-example-valid.conf"

        private val VERSION = Version(1, 0)
    }

    @InjectService(timeout = 4000)
    lateinit var configurationValidatorFactory: ConfigurationValidatorFactory

    @Test
    fun `Fail validation on validate some messaging config`() {
        val validator = configurationValidatorFactory.createConfigValidator()
        val config = loadConfig(MESSAGING_CONFIG_FILE)
        try {
            validator.validate(MESSAGING_CONFIG, VERSION, config, false)
        } catch (e: ConfigurationValidationException) {
            assertEquals(MESSAGING_CONFIG, e.key)
            assertEquals(2, e.errors.size)
        }
    }

    @Test
    fun `Fail validation on some messaging config while trying apply defaults`() {
        val validator = configurationValidatorFactory.createConfigValidator()
        val config = loadConfig(MESSAGING_CONFIG_FILE)
        try {
            validator.validate(MESSAGING_CONFIG, VERSION, config, true)
        } catch (e: ConfigurationValidationException) {
            assertEquals(MESSAGING_CONFIG, e.key)
            assertEquals(2, e.errors.size)
        }
    }

    @Test
    fun `Pass validation on some messaging config while trying apply defaults`() {
        val validator = configurationValidatorFactory.createConfigValidator()
        val config = loadConfig(VALID_MESSAGING_CONFIG_FILE)
        val outputConfig = validator.validate(MESSAGING_CONFIG, VERSION, config, true)
        assertThat(outputConfig).isNotNull
        assertThat(outputConfig.getBoolean(MessagingConfig.Publisher.TRANSACTIONAL)).isEqualTo(false)
        assertThat(outputConfig.getInt(MessagingConfig.Subscription.POLL_TIMEOUT)).isEqualTo(500)
    }

    // Verifies that a default config can be generated from the schema for each section.
    @ParameterizedTest(name = "verify that a sensible default is created for config section: {0}")
    @ValueSource(strings = [
        MESSAGING_CONFIG,
        CRYPTO_CONFIG,
        DB_CONFIG,
        FLOW_CONFIG,
        P2P_LINK_MANAGER_CONFIG,
        P2P_GATEWAY_CONFIG,
        REST_CONFIG,
        SANDBOX_CONFIG,
        RECONCILIATION_CONFIG,
        SECRETS_CONFIG,
        MEMBERSHIP_CONFIG
    ])
    fun `verify that a sensible default is created when an empty config is provided`(section: String) {
        val validator = configurationValidatorFactory.createConfigValidator()
        val outputConfig = validator.getDefaults(section, VERSION)
        assertThat(outputConfig).isNotNull
    }

    @Test
    fun `attempt to fetch schema for an invalid key`() {
        val validator = configurationValidatorFactory.createConfigValidator()
        val config = loadConfig(MESSAGING_CONFIG_FILE)
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate("corda.bad_key", VERSION, config, false)
        }
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate("bad_key", VERSION, config, false)
        }
    }

    @Test
    fun `attempt to fetch schema at an invalid version`() {
        val validator = configurationValidatorFactory.createConfigValidator()
        val config = loadConfig(MESSAGING_CONFIG_FILE)
        assertThrows<ConfigurationSchemaFetchException> {
            validator.validate(MESSAGING_CONFIG, Version(0, 0), config, false)
        }
    }

    private fun loadConfig(resource: String): SmartConfig {
        val url =
            FrameworkUtil.getBundle(this::class.java).getResource(resource)
                ?: throw IllegalArgumentException("Failed to find $resource")
        return SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseURL(url))
    }
}