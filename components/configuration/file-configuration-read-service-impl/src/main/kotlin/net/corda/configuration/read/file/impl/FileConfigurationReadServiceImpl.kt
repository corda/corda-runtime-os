package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.debug

@Component(service = [ConfigurationReadService::class])
class FileConfigurationReadServiceImpl(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigReadServiceFactory::class)
    private val configReadServiceFactory: ConfigReadServiceFactory
) : ConfigurationReadService {

    companion object {
        private val log = contextLogger()

        @VisibleForTesting
        internal const val CONFIG_FILE_NAME = "config.file"
    }

    private lateinit var configFilePath: String
    private lateinit var fileConfig: Config

    private var bootstrapConfig: Config? = null

    private var subscription: ConfigReadService? = null

    @Volatile
    private var stopped = true

    private val lock = ReentrantLock()

    private val configUpdates = Collections.synchronizedMap(mutableMapOf<ConfigListenerSubscription, ConfigurationHandler>())

    override val isRunning: Boolean get() = !stopped

    private var lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<ConfigurationReadService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.debug { "File configuration read service starting up." }
                if (bootstrapConfig != null) {
                    coordinator.postEvent(ReadFileConfig)
                }
            }
            is BootstrapConfigProvided -> {
                // Let the lifecycle library error the service. The application can listen for error events and
                // respond accordingly.
                requireNotNull(bootstrapConfig) {
                    log.error("An attempt was made to set the bootstrap configuration twice.")
                    "An attempt was made to set the bootstrap configuration twice."
                }
                bootstrapConfig = event.config
                coordinator.postEvent(ReadFileConfig)
            }
            is ReadFileConfig -> {
                readConfigFile()
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
            is StopEvent -> {
                log.debug { "File configuration read service stopping." }
                subscription?.stop()
                subscription = null
                // James didn't use this stop
                stop()
            }
            is ErrorEvent -> log.error("An error occurred in the file configuration read service: ${event.cause.message}.", event.cause)
        }
    }

    override fun bootstrapConfig(config: Config) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun start() {
        lifecycleCoordinator.start()
        lock.withLock {
            configUpdates.forEach { (_, handler) -> handler.onNewConfiguration(setOf(configFilePath), mapOf(configFilePath to fileConfig)) }
            stopped = false
        }
    }

    override fun stop() {
        lifecycleCoordinator.stop()
        lock.withLock {
            if (!stopped) {
                configUpdates.clear()
                stopped = true
            }
        }
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        val sub = ConfigListenerSubscription(this)
        configUpdates[sub] = configHandler
        if (isRunning) {
            configHandler.onNewConfiguration(setOf(configFilePath), mapOf(configFilePath to fileConfig))
        }
        return sub
    }

    private fun readConfigFile() {
        val config = requireNotNull(bootstrapConfig) { "Cannot read the config file without the bootstrap configuration" }
        configFilePath = config.getString(CONFIG_FILE_NAME)
        fileConfig = parseConfigFile()
    }

    private fun parseConfigFile(): Config {
        return try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            ConfigFactory.parseURL(File(configFilePath).toURI().toURL(), parseOptions).resolve()
        } catch (e: ConfigException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        } catch (e: IOException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        }
    }

    private fun unregisterCallback(sub: ConfigListenerSubscription) {
        configUpdates.remove(sub)
    }

    private class ConfigListenerSubscription(private val configReadService: FileConfigurationReadServiceImpl) : AutoCloseable {
        override fun close() {
            configReadService.unregisterCallback(this)
        }
    }
}

