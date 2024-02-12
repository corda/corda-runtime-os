package net.corda.applications.workers.db.test

import com.typesafe.config.Config
import net.corda.application.addon.CordaAddonResolver
import net.corda.application.banner.StartupBanner
import net.corda.applications.workers.db.DBWorker
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.configuration.validation.ExternalChannelsConfigValidator
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Shutdown
import net.corda.processors.db.DBProcessor
import net.corda.schema.configuration.BootConfig.BOOT_DB
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.v5.base.versioning.Version
import net.corda.web.api.Endpoint
import net.corda.web.api.WebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.osgi.framework.Bundle
import java.io.InputStream

/**
 * Tests handling of command-line arguments for the [DBWorker].
 *
 * Since the behaviour is almost identical across workers, we do not have equivalent tests for the other worker types.
 */
class ConfigTests {

    val defaultArgs = listOf("-spassphrase=password", "-ssalt=salt")
    val applicationBanner = ApplicationBanner(DummyStartupBanner(), mock<CordaAddonResolver> {
        on { findAll() } doReturn emptyList()})

    val  secretsServiceFactoryResolver = mock<SecretsServiceFactoryResolver> {
        on { findAll() } doReturn listOf(EncryptionSecretsServiceFactory())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `instance ID, topic prefix, workspace dir, temp dir, max size, messaging params, database params and additional params are passed through to the processor`() {
        val dbProcessor = DummyDBProcessor()
        val dbWorker = DBWorker(
            dbProcessor,
            mock(),
            DummyShutdown(),
            DummyLifecycleRegistry(),
            DummyWebServer(),
            DummyValidatorFactory(),
            DummyPlatformInfoProvider(),
            applicationBanner,
            secretsServiceFactoryResolver
        )
        val args = defaultArgs + arrayOf(
            FLAG_INSTANCE_ID, VAL_INSTANCE_ID,
            FLAG_TOPIC_PREFIX, VALUE_TOPIC_PREFIX,
            FLAG_MAX_SIZE, VALUE_MAX_SIZE,
            FLAG_MSG_PARAM, "$MSG_KEY_ONE=$MSG_VAL_ONE",
            FLAG_DB_PARAM, "$DB_KEY_ONE=$DB_VAL_ONE"
        )

        dbWorker.startup(args.toTypedArray())
        val config = dbProcessor.config!!

        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            BOOT_MAX_ALLOWED_MSG_SIZE,
            WORKSPACE_DIR,
            TEMP_DIR,
            "$BOOT_KAFKA_COMMON.$MSG_KEY_ONE",
            "$BOOT_DB.$DB_KEY_ONE"
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)

        assertEquals(VAL_INSTANCE_ID.toInt(), config.getAnyRef(INSTANCE_ID))
        assertEquals(VALUE_TOPIC_PREFIX, config.getAnyRef(TOPIC_PREFIX))
        assertEquals(VALUE_MAX_SIZE, config.getString(BOOT_MAX_ALLOWED_MSG_SIZE))
        assertEquals(MSG_VAL_ONE, config.getAnyRef("$BOOT_KAFKA_COMMON.$MSG_KEY_ONE"))
        assertEquals(DB_VAL_ONE, config.getAnyRef("$BOOT_DB.$DB_KEY_ONE"))
    }

    @Test
    fun `other params are not passed through to the processor`() {
        val dbProcessor = DummyDBProcessor()
        val dbWorker = DBWorker(
            dbProcessor,
            mock(),
            DummyShutdown(),
            DummyLifecycleRegistry(),
            DummyWebServer(),
            DummyValidatorFactory(),
            DummyPlatformInfoProvider(),
            applicationBanner,
            secretsServiceFactoryResolver
        )

        val args = defaultArgs + arrayOf(
            FLAG_MONITOR_PORT, "9999"
        )
        dbWorker.startup(args.toTypedArray())
        val config = dbProcessor.config!!

        // Instance ID and topic prefix are always present, with default values if none are provided.
        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            BOOT_MAX_ALLOWED_MSG_SIZE,
            WORKSPACE_DIR,
            TEMP_DIR
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)
    }

    @Test
    fun `defaults are provided for instance Id, topic prefix, workspace dir, temp dir and reconciliation`() {
        val dbProcessor = DummyDBProcessor()
        val dbWorker = DBWorker(
            dbProcessor,
            mock(),
            DummyShutdown(),
            DummyLifecycleRegistry(),
            DummyWebServer(),
            DummyValidatorFactory(),
            DummyPlatformInfoProvider(),
            applicationBanner,
            secretsServiceFactoryResolver
        )

        dbWorker.startup(defaultArgs.toTypedArray())
        val config = dbProcessor.config!!

        val expectedKeys = setOf(
            INSTANCE_ID,
            TOPIC_PREFIX,
            BOOT_MAX_ALLOWED_MSG_SIZE,
            WORKSPACE_DIR,
            TEMP_DIR
        )
        val actualKeys = config.entrySet().map { entry -> entry.key }.toSet()
        assertEquals(expectedKeys, actualKeys)

        // The default for instance ID is randomly generated, so its value can't be tested for.
        assertEquals(DEFAULT_TOPIC_PREFIX, config.getAnyRef(TOPIC_PREFIX))
    }

    @Test
    fun `multiple messaging params can be provided`() {
        val dbProcessor = DummyDBProcessor()
        val dbWorker = DBWorker(
            dbProcessor,
            mock(),
            DummyShutdown(),
            DummyLifecycleRegistry(),
            DummyWebServer(),
            DummyValidatorFactory(),
            DummyPlatformInfoProvider(),
            applicationBanner,
            secretsServiceFactoryResolver
        )

        val args = defaultArgs + arrayOf(
            FLAG_MSG_PARAM, "$MSG_KEY_ONE=$MSG_VAL_ONE",
            FLAG_MSG_PARAM, "$MSG_KEY_TWO=$MSG_VAL_TWO"
        )
        dbWorker.startup(args.toTypedArray())
        val config = dbProcessor.config!!

        assertEquals(MSG_VAL_ONE, config.getAnyRef("$BOOT_KAFKA_COMMON.$MSG_KEY_ONE"))
        assertEquals(MSG_VAL_TWO, config.getAnyRef("$BOOT_KAFKA_COMMON.$MSG_KEY_TWO"))
    }

    @Test
    fun `multiple database params can be provided`() {
        val dbProcessor = DummyDBProcessor()
        val dbWorker = DBWorker(
            dbProcessor,
            mock(),
            DummyShutdown(),
            DummyLifecycleRegistry(),
            DummyWebServer(),
            DummyValidatorFactory(),
            DummyPlatformInfoProvider(),
            applicationBanner,
            secretsServiceFactoryResolver
        )
        val args = defaultArgs + arrayOf(
            FLAG_DB_PARAM, "$DB_KEY_ONE=$DB_VAL_ONE",
            FLAG_DB_PARAM, "$DB_KEY_TWO=$DB_VAL_TWO"
        )
        dbWorker.startup(args.toTypedArray())
        val config = dbProcessor.config!!

        assertEquals(DB_VAL_ONE, config.getAnyRef("$BOOT_DB.$DB_KEY_ONE"))
        assertEquals(DB_VAL_TWO, config.getAnyRef("$BOOT_DB.$DB_KEY_TWO"))
    }

    /** A [DBProcessor] that stores the passed-in config in [config] for inspection. */
    private class DummyDBProcessor : DBProcessor {
        var config: SmartConfig? = null

        override fun start(bootConfig: SmartConfig) {
            this.config = bootConfig
        }

        override fun stop() = throw NotImplementedError()
    }

    /** A no-op [Shutdown]. */
    private class DummyShutdown : Shutdown {
        override fun shutdown(bundle: Bundle) = Unit
    }

    private class DummyLifecycleRegistry : LifecycleRegistry {
        override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> {
            TODO("Not yet implemented")
        }

    }

    private class DummyWebServer : WebServer {
        override fun stop() = throw NotImplementedError()
        override fun registerEndpoint(endpoint: Endpoint) = Unit
        override fun removeEndpoint(endpoint: Endpoint)  = Unit
        override val port = 7000
        override val endpoints: Set<Endpoint>
            get() = emptySet()

        override fun start(port: Int) = Unit
    }

    private class DummyValidatorFactory : ConfigurationValidatorFactory {
        override fun createConfigValidator(): ConfigurationValidator = DummyConfigurationValidator()
        override fun createCordappConfigValidator(): ConfigurationValidator = DummyConfigurationValidator()
        override fun createExternalChannelsConfigValidator(): ExternalChannelsConfigValidator {
            throw NotImplementedError()
        }
    }

    private class DummyConfigurationValidator : ConfigurationValidator {
        override fun validate(key: String, version: Version, config: SmartConfig, applyDefaults: Boolean): SmartConfig = config

        override fun validate(key: String, config: SmartConfig, schemaInput: InputStream, applyDefaults: Boolean) = config

        override fun getDefaults(key: String, version: Version): Config = SmartConfigImpl.empty()
    }

    private class DummyPlatformInfoProvider : PlatformInfoProvider {
        override val activePlatformVersion: Int
            get() = 5
        override val localWorkerPlatformVersion: Int
            get() = 5000
        override val localWorkerSoftwareVersion: String
            get() = "5.0.0.0"

    }
    private class DummyStartupBanner : StartupBanner {
        override fun get(name: String, version: String): String {
            return "foo"
        }
    }
}
