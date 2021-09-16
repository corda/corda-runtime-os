package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [ConfigurationReadService::class])
class FileConfigurationReadServiceImpl : ConfigurationReadService {

    companion object {
        private val log = contextLogger()

        @VisibleForTesting
        internal const val CONFIG_FILE_NAME = "config.file"
    }

    private lateinit var configFilePath: String
    private lateinit var config: Config

    @Volatile
    private var stopped = true

    private val lock = ReentrantLock()

    private val configUpdates = Collections.synchronizedMap(mutableMapOf<ConfigListenerSubscription, ConfigurationHandler>())

    override val isRunning: Boolean get() = !stopped

    override fun bootstrapConfig(config: Config) {
        configFilePath = config.getString(CONFIG_FILE_NAME)
        this.config = parseConfigFile()
    }

    override fun start() {
        lock.withLock {
            configUpdates.forEach { (_, handler) -> handler.onNewConfiguration(setOf(configFilePath), mapOf(configFilePath to config)) }
            stopped = false
        }
    }

    override fun stop() {
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
            configHandler.onNewConfiguration(setOf(configFilePath), mapOf(configFilePath to config))
        }
        return sub
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

