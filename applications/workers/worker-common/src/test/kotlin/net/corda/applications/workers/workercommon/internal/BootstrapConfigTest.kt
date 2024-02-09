 package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.ConfigFactory
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Path

class BootstrapConfigTest {
    private val mockSecretsServiceFactoryResolver = mock<SecretsServiceFactoryResolver> {
        on { findAll() }.thenReturn(listOf(EncryptionSecretsServiceFactory()))
    }
    private val mockConfigurationValidator = mock<ConfigurationValidator>()
    private val defaultWorkerParams = DefaultWorkerParams(1234).also {
        it.secrets = mapOf(
            "salt" to "foo",
            "passphrase" to "bar",
        )
    }
    private val extraParamsMap = listOf(
        ConfigFactory.parseMap(mapOf("fred.age" to "12", "fred.hair" to "none"))
    )
    private val file1 = Path.of(this::class.java.classLoader.getResource("test1.properties")!!.toURI())
    private val file2 = Path.of(this::class.java.classLoader.getResource("test2.properties")!!.toURI())

    @Test
    fun `when file is provided use it as fallback`() {
        defaultWorkerParams.configFiles = listOf(file1)
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator,
            extraParamsMap
        )

        assertThat(config.getString("fred.age")).isEqualTo("12")
        assertThat(config.getString("fred.street")).isEqualTo("sesame")
    }

    @Test
    fun `when 2 files are provided use last (properties)`() {
        defaultWorkerParams.configFiles = listOf(file1, file2)
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator,
            extraParamsMap
        )

        assertThat(config.getString("fred.street")).isEqualTo("London Wall")
    }

    @Test
    fun `when file is provided cmd line overrides (properties)`() {
        defaultWorkerParams.configFiles = listOf(file1)
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator,
            extraParamsMap
        )

        assertThat(config.getString("fred.hair")).isEqualTo("none")
    }

    @Test
    fun `full config can be provided in file (json)`() {
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            DefaultWorkerParams(1234).also {
                it.configFiles =
                    listOf(Path.of(this::class.java.classLoader.getResource("example-config.json")!!.toURI()))
            },
            mockConfigurationValidator
        )

        assertSoftly { softly ->
            softly.assertThat(config.getString("${BootConfig.BOOT_CRYPTO}.hsmId")).isEqualTo("hsm-id")

            softly.assertThat(config.getString("${BootConfig.BOOT_DB}.database.jdbc.directory"))
                .isEqualTo("jdbc-dir")
            softly.assertThat(config.getString("${BootConfig.BOOT_DB}.database.jdbc.url"))
                .isEqualTo("jdbc-url")
            softly.assertThat(config.getString("${BootConfig.BOOT_DB}.database.jdbc.url_messagebus"))
                .isEqualTo("jdbc-url-msg-bus")
            softly.assertThat(config.getString("${BootConfig.BOOT_DB}.database.pass"))
                .isEqualTo("db-pass")
            softly.assertThat(config.getString("${BootConfig.BOOT_DB}.database.user"))
                .isEqualTo("db-user")

            softly.assertThat(config.getString(ConfigKeys.TEMP_DIR)).isEqualTo("dir-tmp")
            softly.assertThat(config.getString(ConfigKeys.WORKSPACE_DIR)).isEqualTo("dir-workspace")

            softly.assertThat(config.getInt("instanceId")).isEqualTo(666)

            softly.assertThat(config.getString("kafka.common.bus.busType")).isEqualTo("kafka-bustype")

            softly.assertThat(config.getInt("maxAllowedMessageSize")).isEqualTo(999)

            softly.assertThat(config.getString("rest.tls.keystore.password")).isEqualTo("tls-pass")
            softly.assertThat(config.getString("rest.tls.keystore.path")).isEqualTo("tls-path")

            softly.assertThat(config.hasPath("secrets")).isFalse

            val stateManagerConfig = config.getConfig(BootConfig.BOOT_STATE_MANAGER)
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.FLOW_CHECKPOINT,
                driver = "flowCheckpoint-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.FLOW_MAPPING,
                driver = "flowMapping-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.KEY_ROTATION,
                driver = "keyRotation-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.TOKEN_POOL_CACHE,
                driver = "tokenPoolCache-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.P2P_SESSION,
                driver = "p2pSession-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )
            assertStateType(
                softly, stateManagerConfig, StateManagerConfig.StateType.FLOW_STATUS,
                driver = "flowStatus-driver",
                minSize = 111, maxSize = 222, idleTimeout = 333, maxLifetime = 444, keepAlive = 555, validationTimeout = 666
            )

            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_SESSION)).isEqualTo(2)
            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_IN)).isEqualTo(3)
            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_OUT)).isEqualTo(4)
        }
    }

    @Test
    fun `config with defaults`() {
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator
        )

        assertSoftly { softly ->
            softly.assertThat(config.getString("dir.tmp")).isEqualTo(ConfigDefaults.TEMP_DIR)
            softly.assertThat(config.getString("dir.workspace")).isEqualTo(ConfigDefaults.WORKSPACE_DIR)
            softly.assertThat(config.getInt("instanceId")).isNotNull
            softly.assertThat(config.getInt("maxAllowedMessageSize")).isEqualTo(972800)
            softly.assertThat(config.getString("topicPrefix")).isEqualTo("")
            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_SESSION))
                .isEqualTo(1)
            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_IN))
                .isEqualTo(1)
            softly.assertThat(config.getInt(BootConfig.WORKER_MEDIATOR_REPLICAS_FLOW_MAPPER_SESSION_OUT))
                .isEqualTo(1)
        }

    }

    @Test
    fun `extra configs provided override other config that may clash`() {
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator,
            listOf(
                ConfigFactory.parseMap(
                    mapOf(
                        "dir.tmp" to "newConf",
                        "maxAllowedMessageSize" to 0
                    )
                ),
            )
        )

        assertSoftly { softly ->
            softly.assertThat(config.getString("dir.tmp")).isEqualTo("newConf")
            softly.assertThat(config.getString("dir.workspace")).isEqualTo(ConfigDefaults.WORKSPACE_DIR)
            softly.assertThat(config.getInt("instanceId")).isNotNull
            softly.assertThat(config.getInt("maxAllowedMessageSize")).isEqualTo(0)
            softly.assertThat(config.getString("topicPrefix")).isEqualTo("")
        }
    }
}
