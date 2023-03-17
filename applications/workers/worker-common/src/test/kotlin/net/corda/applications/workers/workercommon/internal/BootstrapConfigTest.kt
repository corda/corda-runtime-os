package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
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
        it.secretsParams = mapOf(
            "salt" to "foo",
            "passphrase" to "bar",
        )
    }
    private val extraParamsMap = listOf(
        PathAndConfig("fred", mapOf("age" to "12", "hair" to "none"))
    )
    private val file1 = Path.of(this::class.java.classLoader.getResource("test1.properties").toURI())
    private val file2 = Path.of(this::class.java.classLoader.getResource("test2.properties").toURI())

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
        defaultWorkerParams.configFiles = listOf(file1,file2)
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
                    listOf(Path.of(this::class.java.classLoader.getResource("example-config.json").toURI()))
            },
            mockConfigurationValidator,
            listOf(
                PathAndConfig(BootConfig.BOOT_DB, emptyMap()),
                PathAndConfig(BootConfig.BOOT_CRYPTO, emptyMap()),
                PathAndConfig(BootConfig.BOOT_REST, emptyMap()),
            )
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
        }
    }

    @Test
    fun `config with defaults`() {
        val config = WorkerHelpers.getBootstrapConfig(
            mockSecretsServiceFactoryResolver,
            defaultWorkerParams,
            mockConfigurationValidator,
            listOf(
                PathAndConfig(BootConfig.BOOT_SECRETS, emptyMap()),
                PathAndConfig(BootConfig.BOOT_DB, emptyMap()),
                PathAndConfig(BootConfig.BOOT_CRYPTO, emptyMap()),
                PathAndConfig(BootConfig.BOOT_REST, emptyMap()),
            )
        )

        assertSoftly { softly ->
            softly.assertThat(config.getString("dir.tmp")).isEqualTo(ConfigDefaults.TEMP_DIR)
            softly.assertThat(config.getString("dir.workspace")).isEqualTo(ConfigDefaults.WORKSPACE_DIR)
            softly.assertThat(config.getInt("instanceId")).isNotNull
            softly.assertThat(config.getInt("maxAllowedMessageSize")).isEqualTo(972800)
            softly.assertThat(config.getString("topicPrefix")).isEqualTo("")
        }

    }
}