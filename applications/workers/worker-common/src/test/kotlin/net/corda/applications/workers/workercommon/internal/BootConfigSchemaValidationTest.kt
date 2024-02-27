package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.ConfigFactory
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_DRIVER
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.impl.ConfigurationValidatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class BootConfigSchemaValidationTest {

    private val validator = ConfigurationValidatorFactoryImpl().createConfigValidator()

    @Test
    fun `state manager config with invalid state type is invalid`() {
        val config = Path.of(
            this::class.java.classLoader.getResource("boot-config-schema/invalid-state-type.json")!!.toURI()
        )

        assertThrows<ConfigurationValidationException> {
            validator.validate(
                ConfigKeys.STATE_MANAGER_CONFIG,
                Version(1, 0),
                SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseFile(config.toFile()))
            )
        }
    }

    @Test
    fun `valid state manager boot json is validated`() {
        val config = Path.of(
            this::class.java.classLoader.getResource("boot-config-schema/valid-state-manager-config.json")!!.toURI()
        )

        val result = validator.validate(
            ConfigKeys.STATE_MANAGER_CONFIG,
            Version(1, 0),
            SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseFile(config.toFile()))
        )

        SoftAssertions.assertSoftly { softly ->
            for (type in StateManagerConfig.StateType.values()) {
                assertStateType(
                    softly, result, type, "user", "password",
                    "jdbc:postgresql://localhost:5432/cordacluster?currentSchema=${type.value}"
                )
            }
        }
    }

    @Test
    fun `valid state manager boot json without optional pool config is valid`() {
        val config = Path.of(
            this::class.java.classLoader.getResource("boot-config-schema/valid-state-manager-config-missing-pool.json")!!.toURI()
        )
        val stateType = "flowCheckpoint"

        val result = validator.validate(
            ConfigKeys.STATE_MANAGER_CONFIG,
            Version(1, 0),
            SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseFile(config.toFile()))
        )

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(result.hasPath(stateType))
            val typeConfig = result.getConfig(stateType)
            softly.assertThat(typeConfig.getString(StateManagerConfig.TYPE)).isEqualTo("Database")
            softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_URL))
                .isEqualTo("jdbc:postgresql://localhost:5432/cordacluster?currentSchema=flowCheckpoint")
            softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_USER)).isEqualTo("user")
            softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_PASS)).isEqualTo("password")
            softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_DRIVER)).isEqualTo(DEFAULT_DRIVER)
        }
    }
}